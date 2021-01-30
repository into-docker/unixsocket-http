(ns unixsocket-http.core
  (:require [unixsocket-http.client :as client]
            [unixsocket-http.data :as data]
            [clojure.tools.logging :as log])
  (:refer-clojure :exclude [get])
  (:import [java.net Socket]
           [java.io InputStream]))

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
   (client/create url opts)))

;; ## I/O

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
(defrequest patch)
