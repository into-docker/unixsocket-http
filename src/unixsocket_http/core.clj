(ns unixsocket-http.core
  (:require [clojure.java.io :as io])
  (:refer-clojure :exclude [get])
  (:import [unixsocket_http.impl
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
           [java.io
            InputStream]))

;; ##  Client

(defn client
  ([socket-path]
   (client socket-path identity))
  ([socket-path builder-fn]
   (let [factory (FixedPathUnixSocketFactory. socket-path)
         builder (doto (OkHttpClient$Builder.)
                   (builder-fn)
                   (.socketFactory factory))]
     (.build builder))))

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
  [^Response response {:keys [as] :or {as :string} :as x}]
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
  [{:keys [status] :as response}
   {:keys [throw-exceptions throw-exceptions?]
    :or {throw-exceptions true
         throw-exceptions? true}}]
  (when (and (>= status 400)
             throw-exceptions
             throw-exceptions?)
    (throw
      (ex-info
        (format "HTTP Error: %d" status)
        response)))
  response)

(defn request
  [{:keys [client] :as request}]
  (let [request (normalize-headers request)
        req     (build-request request)]
    (-> ^OkHttpClient client
        (.newCall req)
        (.execute)
        (parse-response request)
        (normalize-headers)
        (handle-response request))))

;; ## Convenience

(defn get
  ([client url]
   (get client url {}))
  ([client url opts]
   (request (merge opts {:client client, :method :get, :url url}))))

(defn head
  ([client url]
   (head client url {}))
  ([client url opts]
   (request (merge opts {:client client, :method :head, :url url}))))

(defn post
  [client url opts]
  (request (merge opts {:client client, :method :post, :url url})))

(defn put
  [client url opts]
  (request (merge opts {:client client, :method :put, :url url})))

(defn patch
  [client url opts]
  (request (merge opts {:client client, :method :patch, :url url})))

(defn delete
  [client url opts]
  (request (merge opts {:client client, :method :delete, :url url})))
