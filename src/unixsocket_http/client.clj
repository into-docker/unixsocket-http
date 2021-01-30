(ns unixsocket-http.client
  (:require [clojure.string :as string])
  (:import [unixsocket_http.impl
            FixedPathTcpSocketFactory
            FixedPathUnixSocketFactory
            SingletonSocketFactory]
           [okhttp3
            Dns
            OkHttpClient
            OkHttpClient$Builder]
           [javax.net SocketFactory]
           [java.net InetAddress Socket URI]
           [java.time Duration]))

;; ## No DNS Lookups
;;
;; DNS lookups in static builds would be a problem. Plus, since the socket is
;; already connected to a specific IP, it's kinda pointless.

(deftype NoDns []
  Dns
  (lookup [_ hostname]
    [(InetAddress/getByAddress hostname (byte-array 4))]))

;; ## SocketFactory

(defn- adapt-url
  "Ensure backwards-compatibility by prefixing filesystem paths with `unix://`"
  [^String url]
  (-> (if-not (re-matches #"[a-zA-Z]+://.*" url)
        (do
          (println "Calling unixsocket-http.core/client with a path instead of a URL is DEPRECATED.")
          (str "unix://" url))
        url)
      (string/replace #"/+$" "")))

(defn- get-port
  [^URI uri & [default]]
  (let [port (.getPort uri)]
    (int
      (or (if (pos? port)
            port
            default)
          (throw
            (IllegalArgumentException.
              (str "Port is required in URI: " uri)))))))

(defn- create-socket-factory-from-uri
  [^URI uri]
  (case (.getScheme uri)
    "unix"  (FixedPathUnixSocketFactory. (.getPath uri))
    "tcp"   (FixedPathTcpSocketFactory. (.getHost uri) (get-port uri))
    "http"  (FixedPathTcpSocketFactory. (.getHost uri) (get-port uri 80))
    "https" (FixedPathTcpSocketFactory. (.getHost uri) (get-port uri 443))
    (throw
      (IllegalArgumentException.
        (str "Can only handle URI schemes 'unix', 'tcp', 'http' and 'https', "
             "given: "
             uri)))))

(defn- create-base-url
  [^URI uri]
  (if (contains? #{"tcp" "unix"} (.getScheme uri))
    "http://localhost"
    (str uri)))

(defn- create-socket-factory
  ^SocketFactory [^String uri-str]
  (let [uri (URI. (adapt-url uri-str))]
    {:factory  (create-socket-factory-from-uri uri)
     :base-url (create-base-url uri)}))

;; ## OkHttpClient

(defn- create-client
  [^SocketFactory factory
   {:keys [builder-fn
           timeout-ms
           read-timeout-ms
           write-timeout-ms
           connect-timeout-ms
           call-timeout-ms
           ;; undocumented options (maybe useful for debugging?)
           ::dns?]
    :or {builder-fn identity
         timeout-ms 0
         dns?       false}}]
   (let [default-timeout (Duration/ofMillis timeout-ms)
         to-timeout #(or (some-> % Duration/ofMillis) default-timeout)
         builder (doto (OkHttpClient$Builder.)
                   (.connectTimeout (to-timeout connect-timeout-ms))
                   (.callTimeout (to-timeout call-timeout-ms))
                   (.readTimeout (to-timeout read-timeout-ms))
                   (.writeTimeout (to-timeout write-timeout-ms))
                   (cond-> (not dns?) (.dns (NoDns.)))
                   (builder-fn)
                   (.socketFactory factory))]
     (.build builder)))


;; ## Client Modes

;; ### Factories

(defn- recreating-client-factory
  [{:keys [factory]} opts]
  (fn [{:keys [as]}]
    (let [factory (SingletonSocketFactory. factory)]
      (cond-> {::client (create-client factory opts)}
        (= as :socket) (assoc ::socket (.getSocket factory))))))

(defn- reusable-client-factory
  [{:keys [factory]} opts]
  (let [client (create-client factory opts)]
    (fn [request]
      (when (= (:as request) :socket)
        (throw
          (IllegalArgumentException.
            (str "Client mode `:reuse` does not allow direct socket access.\n"
                 "See documentation of `unixsocket-http.core/client`."))))
      {::client client})))

(defn- hybrid-client-factory
  [socket-factory opts]
  (let [fresh (recreating-client-factory socket-factory opts)
        reusable (reusable-client-factory socket-factory opts)]
    (fn [{:keys [as] :as request}]
      (if (= as :socket)
        (fresh request)
        (reusable request)))))

;; ### Clients

(defn- make-client
  "Create client map with `::factory` being the client creator function and
   `::base-url` being the URL to use to prefix relative paths."
  [client-factory-fn url opts]
  (let [socket-factory (create-socket-factory url)]
    {::factory  (client-factory-fn socket-factory opts)
     ::base-url (or (:base-url opts) (:base-url socket-factory))}))

(defn- recreating-client
  "Client that will always initiate a new connection."
  [url opts]
  (make-client recreating-client-factory url opts))

(defn- reusable-client
  "Client that will always reuse connections. Note taht this one cannot use
   the `:as :socket` mode."
  [url opts]
  (make-client reusable-client-factory url opts))

(defn- hybrid-client
  "Client that will create a fresh connection when the raw socket is requested."
  [url opts]
  (make-client hybrid-client-factory url opts))

;; ## API

(defn create
  [url {:keys [mode] :or {mode :default} :as opts}]
  (case mode
    :reuse    (reusable-client url opts)
    :recreate (recreating-client url opts)
    :default  (hybrid-client url opts)))

(defn connection
  [{:keys [client] :as request}]
  {:post [(some? %)]}
  ((::factory client) request))

(defn base-url
  [{:keys [client]}]
  (::base-url client))

(defn get-client
  ^OkHttpClient [connection]
  (::client connection))

(defn get-socket
  ^Socket [connection]
  (::socket connection))
