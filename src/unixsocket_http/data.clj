(ns ^:no-doc unixsocket-http.data
  (:require [unixsocket-http.client :as client]
            [clojure.java.io :as io])
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
           [java.io InputStream]))

;; ## Helpers

(defn- normalize-headers
  "Use lowercase string headers."
  [headers]
  (->> (for [[k v] headers]
         [(-> k (name) (.toLowerCase)) v])
       (into {})))

;; ## Request

(defn- build-url
  "Create URL to use for request. This will read the base URL from the
   client contained in the request in case the given url does not include
   a scheme and host."
  ^HttpUrl
  [{:keys [url query-params] :as request}]
  (let [^HttpUrl$Builder builder (or (some-> (HttpUrl/parse url)
                                             (.newBuilder))
                                     (-> (str (client/base-url request)
                                              url)
                                         (HttpUrl/parse)
                                         (.newBuilder)))]
    (doseq [[k v] query-params]
      (.addQueryParameter builder (name k) (str v)))
    (.build builder)))

(defn- build-body
  [{:keys [method headers body]}]
  (cond (instance? InputStream body)
        (StreamingBody. body (get headers "content-type"))

        (string? body)
        (StreamingBody.
          (io/input-stream
            (.getBytes ^String body))
          (get headers "content-type"))

        (#{:post :put :patch} method)
        (RequestBody/create nil "")

        :else nil))

(defn build-request
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
    (doseq [[k v] (normalize-headers headers)]
      (.addHeader builder (name k) (str v)))
    (.build builder)))

;; ## Response

(defn parse-response
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
                  (normalize-headers headers))))
   :body   (let [^ResponseBody body (.body response)]
             (case as
               :string (.string body)
               :stream (.byteStream body)
               :socket (ResponseSocket.
                         (client/get-socket connection)
                         response)))})
