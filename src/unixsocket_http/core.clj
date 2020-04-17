(ns unixsocket-http.core
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:refer-clojure :exclude [get])
  (:import [unixsocket_http.impl
            FixedPathTcpSocketFactory
            FixedPathUnixSocketFactory
            StreamingBody]
           [okhttp3
            HttpUrl
            HttpUrl$Builder
            OkHttpClient
            OkHttpClient$Builder
            Request
            Request$Builder
            RequestBody
            Response
            ResponseBody]
           [javax.net SocketFactory]
           [java.net URI]
           [java.io InputStream]
           [java.time Duration]))

;; ##  Client

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
                   (.socketFactory factory))]
     (.build builder)))

(defn- create-socket-factory
  ^SocketFactory [^String uri-str]
  (let [uri (URI. uri-str)]
    (case (.getScheme uri)
      "unix" (FixedPathUnixSocketFactory. (.getPath uri))
      "tcp"  (FixedPathTcpSocketFactory. (.getHost uri) (.getPort uri))
      (throw
        (IllegalArgumentException.
          (str "Can only handle URI schemes 'unix' and 'tcp', given: " uri-str))))))

(defn- adapt-url
  "Ensure backwards-compatibility by prefixing filesystem paths with `unix://`"
  [^String url]
  (if-not (re-matches #"[a-zA-Z]+://.*" url)
    (do
      (println "Calling unixsocket-http.core/client with a path instead of a URL is DEPRECATED.")
      (str "unix://" url))
    url))

(defn client
  "Create a new HTTP client that utilises the given TCP or UNIX domain socket to
   perform HTTP communication. Options are:

   - `:timeout-ms`: A timeout that will be used for connect/call/read/write
     timeout settings, unless they are explicitly specified using the
     following options:
     - `:connect-timeout-ms`
     - `:call-timeout-ms`
     - `:read-timeout-ms`
     - `:write-timeout-ms`
   - `:builder-fn`: a function that will be called on the underlying
     `OkHttpClient$Builder` and can be used to perform arbitrary adjustments
     to the HTTP client (with exception of the socket factory.

   Examples:

   ```
   (client \"unix:///var/run/docker.sock\")
   (client \"tcp://127.0.0.1:6537\")
   ```

   "
  ([url]
   (client url {}))
  ([url opts]
   (-> (adapt-url url)
       (create-socket-factory)
       (create-client opts))))

;; ## Request

(defn- normalize-headers
  [{:keys [headers] :as data}]
  (->> (for [[k v] headers]
         [(-> k (name) (.toLowerCase)) v])
       (into {})
       (assoc data :headers)))

(defn- build-url
  ^HttpUrl
  [{:keys [url query-params]}]
  (let [^HttpUrl$Builder builder (->> (str "http://localhost" url)
                                      (HttpUrl/parse)
                                      (.newBuilder))]
    (doseq [[k v] query-params]
      (.addQueryParameter builder (name k) (str v)))
    (.build builder)))

(defn- build-body
  [{:keys [method headers body]}]
  (cond (instance? InputStream body)
        (StreamingBody. body (clojure.core/get headers "content-type"))

        (string? body)
        (StreamingBody.
          (io/input-stream
            (.getBytes ^String body))
          (clojure.core/get headers "content-type"))

        (#{:post :put :patch} method)
        (RequestBody/create nil "")

        :else nil))

(defn- build-request
  ^Request
  [{:keys [method headers] :as request}]
  (let [^Request$Builder builder (doto (Request$Builder.)
                                   (.method
                                     (.toUpperCase (name method))
                                     (build-body request))
                                   (.url (build-url request)))]
    (doseq [[k v] headers]
      (.addHeader builder (name k) (str v)))
    (.build builder)))

(defn- parse-response
  [^Response response {:keys [method as] :or {as :string} :as x}]
  {:status (.code response)
   :headers (let [it (.. response headers iterator)]
              (loop [headers {}]
                (if (.hasNext it)
                  (let [^kotlin.Pair pair (.next it)]
                    (recur
                      (assoc headers (.getFirst pair) (.getSecond pair))))
                  headers)))
   :body   (let [^ResponseBody body (.body response)]
             (case as
               :string (.string body)
               :stream (.byteStream body)
               :socket nil))})

(defn- handle-response
  [{:keys [status body] :as response}
   {:keys [throw-exceptions throw-exceptions? as]
    :or {throw-exceptions true
         throw-exceptions? true}}]
  (log/tracef "[unixsocket-http] <--- %s" (pr-str response))
  (when (and (>= status 400)
             throw-exceptions
             throw-exceptions?)
    (when (= :stream as)
      (.close ^InputStream body))
    (throw
      (ex-info
        (format "HTTP Error: %d" status)
        response)))
  response)

(defn request
  [request]
  (let [request (normalize-headers request)
        req     (build-request request)]
    (log/tracef "[unixsocket-http] ---> %s" (pr-str (dissoc request :client)))
    (-> ^OkHttpClient (:client request)
        (.newCall req)
        (.execute)
        (parse-response request)
        (normalize-headers)
        (handle-response request))))

;; ## Convenience

(defmacro ^:private defrequest
  [method]
  `(defn ~method
     ~(format
        (str "Perform a %s request against the given client. Options are:%n%n"
             "  - `:headers`:          a map of string/string pairs that will be sent as headers.%n"
             "  - `:query-params`:     a map of string/string pairs that will be sent as the query string.%n"
             "  - `:as`:               if set to `:stream` the response's body will be an `InputStream` value (that needs to be closed after consuming).%n"
             "  - `:throw-exceptions`: if set to `false` all responses will be returned and no exception is thrown on HTTP error codes.%n")
        (.toUpperCase (name method)))
     ([~'client ~'url]
      (~method ~'client ~'url {}))
     ([~'client ~'url ~'opts]
      (request (merge ~'opts {:client ~'client, :method ~(keyword (name method)) , :url ~'url})))))

(defrequest get)
(defrequest head)
(defrequest post)
(defrequest put)
(defrequest delete)
