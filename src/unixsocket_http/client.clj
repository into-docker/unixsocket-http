(ns unixsocket-http.client
  (:import [unixsocket_http.impl
            FixedPathTcpSocketFactory
            FixedPathUnixSocketFactory
            SingletonSocketFactory]
           [okhttp3
            OkHttpClient
            OkHttpClient$Builder]
           [javax.net SocketFactory]
           [java.net InetSocketAddress Socket URI]
           [java.time Duration]))

;; ## SocketFactory

(defn- adapt-url
  "Ensure backwards-compatibility by prefixing filesystem paths with `unix://`"
  [^String url]
  (if-not (re-matches #"[a-zA-Z]+://.*" url)
    (do
      (println "Calling unixsocket-http.core/client with a path instead of a URL is DEPRECATED.")
      (str "unix://" url))
    url))

(defn- create-socket-factory
  ^SocketFactory [^String uri-str]
  (let [uri (URI. (adapt-url uri-str))]
    (case (.getScheme uri)
      "unix" (FixedPathUnixSocketFactory. (.getPath uri))
      "tcp"  (FixedPathTcpSocketFactory. (.getHost uri) (.getPort uri))
      (throw
        (IllegalArgumentException.
          (str "Can only handle URI schemes 'unix' and 'tcp', given: " uri-str))))))

(defn- create-singleton-socket-factory
  ^SingletonSocketFactory
  [^String uri-str]
  (SingletonSocketFactory.
    (create-socket-factory uri-str)))

;; ## OkHttpClient

(defn- create-client
  [^SocketFactory factory
   {:keys [builder-fn
           timeout-ms
           read-timeout-ms
           write-timeout-ms
           connect-timeout-ms
           call-timeout-ms]
    :or {builder-fn identity
         timeout-ms 0}}]
   (let [default-timeout (Duration/ofMillis timeout-ms)
         to-timeout #(or (some-> % Duration/ofMillis) default-timeout)
         builder (doto (OkHttpClient$Builder.)
                   (.connectTimeout (to-timeout connect-timeout-ms))
                   (.callTimeout (to-timeout call-timeout-ms))
                   (.readTimeout (to-timeout read-timeout-ms))
                   (.writeTimeout (to-timeout write-timeout-ms))
                   (builder-fn)
                   (.socketFactory factory))
         client (delay (.build builder))]
     (.build builder)))

;; ## Client Modes

(defn- recreating-client
  [url opts]
  (fn [request]
    (let [factory (create-singleton-socket-factory url)]
      {::client (create-client factory opts)
       ::socket (.getSocket factory)})))

(defn- reusable-client
  [url opts]
  (let [client (-> (create-socket-factory url)
                   (create-client opts))]
    (fn [request]
      (when (= (:as request) :socket)
        (throw
          (IllegalArgumentException.
            (str "Client mode `:reuse` does not allow direct socket access.\n"
                 "See documentation of `unixsocket-http.core/client`."))))
      {::client client})))

(defn- hybrid-client
  "Client that will create a fresh connection when the raw socket is requested."
  [url opts]
  (let [fresh (recreating-client url opts)
        client (reusable-client url opts)]
    (fn [{:keys [as] :as request}]
      (if (= as :socket)
        (fresh request)
        (client request)))))

;; ## API

(defn create
  [url {:keys [mode] :or {mode :default} :as opts}]
  (case mode
    :reuse    (reusable-client url opts)
    :recreate (recreating-client url opts)
    :default  (hybrid-client url opts)))

(defn connection
  [{:keys [client] :as request}]
  (client request))

(defn get-client
  ^OkHttpClient [connection]
  (::client connection))

(defn get-socket
  ^Socket [connection]
  (::socket connection))
