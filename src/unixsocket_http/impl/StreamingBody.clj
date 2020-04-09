(ns unixsocket-http.impl.StreamingBody
  "Implementation of OkHttp's RequestBody that streams some data."
  (:gen-class
    :name         unixsocket_http.impl.StreamingBody
    :extends      okhttp3.RequestBody
    :init         init
    :state        state
    :constructors {[java.io.InputStream] []
                   [java.io.InputStream String] []})
  (:import [okhttp3 MediaType]
           [okio BufferedSink Okio]
           [java.io InputStream]))

;; ## Constructor

(def ^:private media-type-octetstream
  (MediaType/get "application/octet-stream"))

(defn -init
  ([^InputStream stream]
   [[]  {:stream     stream
         :media-type media-type-octetstream}])
  ([^InputStream stream ^String content-type]
   [[] {:stream     stream
        :media-type (if content-type
                      (MediaType/get content-type)
                      media-type-octetstream)}]))

(defn- stream
  ^InputStream [^unixsocket_http.impl.StreamingBody this]
  (:stream (.-state this)))

(defn- media-type
  ^InputStream [^unixsocket_http.impl.StreamingBody this]
  (:media-type (.-state this)))

;; ## Methods

(defn -contentType
  [this]
  (media-type this))

(defn -contentLength
  [this]
  (let [length (.available (stream this))]
    (if (pos? length)
      length
      -1)))

(defn -writeTo
  [this ^BufferedSink sink]
  (with-open [source (Okio/source (stream this))]
    (.writeAll sink source)))
