(ns unixsocket-http.core
  (:require [unixsocket-http.client :as client]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:refer-clojure :exclude [get])
  (:import [unixsocket_http.impl
            ResponseSocket
            StreamingBody]
           [okhttp3
            HttpUrl
            HttpUrl$Builder
            Request
            Request$Builder
            RequestBody
            Response
            ResponseBody]
           [java.net Socket]
           [java.io InputStream]))

;; ##  Client

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
   - `:builder-fn`: A function that will be called on the underlying
     `OkHttpClient$Builder` and can be used to perform arbitrary adjustments
     to the HTTP client (with exception of the socket factory.
   - `:mode`: A keyword describing the connection behaviour:
     - `:default`: A reusable client will be created, except for raw socket
       access where a new connection will be established.
     - `:reuse`: A reusable client will be created, and raw socket access will
       not be possible.
     - `:recreate`: For every request a new client will be created; this might
       be useful if you encounter problems with sharing the client across
       threads.

   Examples:

   ```
   (client \"unix:///var/run/docker.sock\")
   (client \"tcp://127.0.0.1:6537\")
   ```

   "
  ([url]
   (client url {}))
  ([url opts]
   (client/create url opts)))

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
  (let [^HttpUrl$Builder builder (->> (if (re-matches #"https?://.*" url)
                                        url
                                        (str "http://localhost" url))
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
  (when-not (#{:get :post :put :delete :patch :head} method)
    (throw
      (IllegalArgumentException.
        (str "Invalid HTTP method keyword supplied: " method))))
  (let [^Request$Builder builder (doto (Request$Builder.)
                                   (.method
                                     (.toUpperCase (name method))
                                     (build-body request))
                                   (.url (build-url request)))]
    (doseq [[k v] headers]
      (.addHeader builder (name k) (str v)))
    (.build builder)))

(defn- parse-response
  [^Response response
   {:keys [as] :or {as :string}}
   connection]
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
               :socket (ResponseSocket.
                         (client/get-socket connection)
                         response)))})

(defn- handle-response
  [{:keys [status body] :as response}
   {:keys [throw-exceptions throw-exceptions? as]
    :or {throw-exceptions true
         throw-exceptions? true}}]
  (log/tracef "[unixsocket-http] <--- %s" (pr-str response))
  (when (and (>= status 400)
             throw-exceptions
             throw-exceptions?)
    (case as
      :stream (.close ^InputStream body)
      :socket (.close ^Socket body)
      nil)
    (throw
      (ex-info
        (format "HTTP Error: %d" status)
        response)))
  response)

(defn request
  [request]
  (let [request    (normalize-headers request)
        req        (build-request request)
        connection (client/connection request)]
    (log/tracef "[unixsocket-http] ---> %s" (pr-str (dissoc request :client)))
    (-> (client/get-client connection)
        (.newCall req)
        (.execute)
        (parse-response request connection)
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
