(ns unixsocket-http.core
  (:require [unixsocket-http.client :as client]
            [unixsocket-http.data :as data]
            [clojure.tools.logging :as log])
  (:refer-clojure :exclude [get]))

;; ##  Client

(defn client
  "Create a new HTTP client that utilises the given TCP or UNIX domain socket to
   perform HTTP communication. Options are:

   - `:base-url`: A string containing the base URL to use for HTTP calls; by
     default this will be the URL given to create the client.
   - `:timeout-ms`: A timeout that will be used for connect/call/read/write
     timeout settings, unless they are explicitly specified using the
     following options:
     - `:connect-timeout-ms`
     - `:call-timeout-ms`
     - `:read-timeout-ms`
     - `:write-timeout-ms`
   - `:mode`: A keyword describing the connection behaviour:
     - `:default`: A reusable client will be created, except for raw socket
       access where a new connection will be established.
     - `:reuse`: A reusable client will be created, and raw socket access will
       not be possible.
     - `:recreate`: For every request a new client will be created; this might
       be useful if you encounter problems with sharing the client across
       threads.
   - `:builder-fn`: A function that will be called on the underlying
     `OkHttpClient$Builder` and can be used to perform arbitrary adjustments
     to the HTTP client (with exception of the socket factory).

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

;; ## I/O

(defn- throw?
  [{:keys [status]}
   {:keys [throw-exceptions throw-exceptions?]
    :or {throw-exceptions  true
         throw-exceptions? true}}]
  (and (>= status 400)
       throw-exceptions
       throw-exceptions?))

(letfn [(without-body [{:keys [^java.io.Closeable body] :as response}]
          (.close body)
          (dissoc response :body))]
  (defn- prepare-ex-data!
    [response {:keys [throw-entire-message? as]}]
    (if-not throw-entire-message?
      (case as
        (:stream :socket) (without-body response)
        response)
      response)))

(defn- throw-http-exception!
  [{:keys [status] :as response} opts]
  (when (throw? response opts)
    (let [edata (prepare-ex-data! response opts)]
      (throw
       (ex-info
        (format "HTTP Error: %d" status)
        edata)))))

(defn- handle-response
  [response opts]
  (log/tracef "[unixsocket-http] <--- %s" (pr-str response))
  (when (throw? response opts)
    (throw-http-exception! response opts))
  response)

(defn request
  "Perform an HTTP request. Options are:

   - `:client` (required): the `client` to use for the request.
   - `:method` (required): a keyword indicating the HTTP method to use.
   - `:url` (required): the URL/path to send the request to.
   - `:body`: request body, if supported by the HTTP method.
   - `:headers`: a map of string/string pairs that will be sent as headers.
   - `:query-params`: a map of string/string pairs that will be sent as the query string.
   - `:as`: if set to `:stream` the response's body will be an `InputStream` value (that needs to be closed after consuming).
   - `:throw-exceptions?`: if set to `false` all responses will be returned and no exception is thrown on HTTP error codes.
   - `:throw-entire-message?`: if set to `true` HTTP exceptions will contain the full response as `ex-data`; streams and sockets will not be closed automatically!
  "
  [request]
  (let [req        (data/build-request request)
        connection (client/connection request)]
    (log/tracef "[unixsocket-http] ---> %s" (pr-str (dissoc request :client)))
    (-> (client/get-client connection)
        (.newCall req)
        (.execute)
        (data/parse-response request connection)
        (handle-response request))))

;; ## Convenience

(defmacro ^:private defrequest
  [method]
  `(defn ~method
     ~(format
       (str "Perform a %s request against the given client. Options are:%n%n"
            "  - `:headers`: a map of string/string pairs that will be sent as headers.%n"
            "  - `:query-params`: a map of string/string pairs that will be sent as the query string.%n"
            "  - `:as`: if set to `:stream` the response's body will be an `InputStream` value (that needs to be closed after consuming).%n"
            "  - `:throw-exceptions?`: if set to `false` all responses will be returned and no exception is thrown on HTTP error codes.%n"
            "  - `:throw-entire-message?`: if set to `true` HTTP exceptions will contain the full response as `ex-data`; streams and sockets will not be closed automatically!%n")
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
(defrequest patch)
