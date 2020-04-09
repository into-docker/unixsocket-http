(ns unixsocket-http.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [unixsocket-http.core :as http])
  (:import [org.newsclub.net.unix
            AFUNIXServerSocket
            AFUNIXSocketAddress]
           [fi.iki.elonen
            NanoHTTPD
            NanoHTTPD$IHTTPSession
            NanoHTTPD$ServerSocketFactory
            NanoHTTPD$Response$Status]
           [java.io File]))

;; ## Fixtures

(defn- read-body
  [^NanoHTTPD$IHTTPSession session]
  (when-let [len (.. session
                     (getHeaders)
                     (get "content-length"))]
    (let [in (.getInputStream session)
          buf (byte-array (Integer. ^String len))]
      (.read in buf)
      (String. buf))))

(defn- start-nanohttpd
  [^File socket-file]
  (let [address (AFUNIXSocketAddress. socket-file)]
    (doto (proxy [NanoHTTPD] [0]
            (serve [^NanoHTTPD$IHTTPSession session]
              (let [body (read-body session)
                    method (str (.getMethod session))]
                (case (.getUri session)
                  "/ok"
                  (NanoHTTPD/newFixedLengthResponse "OK")

                  "/head"
                  (NanoHTTPD/newFixedLengthResponse "")

                  "/echo"
                  (NanoHTTPD/newFixedLengthResponse
                    NanoHTTPD$Response$Status/OK
                    "text/plain"
                    body)

                  "/fail"
                  (NanoHTTPD/newFixedLengthResponse
                    NanoHTTPD$Response$Status/INTERNAL_ERROR
                    "text/plain"
                    "FAIL")

                  (NanoHTTPD/newFixedLengthResponse
                    NanoHTTPD$Response$Status/NOT_FOUND
                    "text/plain"
                    "NOT_FOUND")))))
      (.setServerSocketFactory
        (proxy [NanoHTTPD$ServerSocketFactory] []
          (create []
            (AFUNIXServerSocket/forceBindOn address))))
      (.start NanoHTTPD/SOCKET_READ_TIMEOUT false))))

(defn- create-socket-server
  []
  (let [socket-file (doto (File/createTempFile "http" ".sock")
                      (.delete))]
    {:client (http/client (.getCanonicalPath socket-file))
     :server (start-nanohttpd socket-file)}))

(defmacro with-socket-server
  [client & body]
  `(let [data# (create-socket-server)
         server# (:server data#)
         ~client (:client data#)]
     (try
       (do ~@body)
       (finally
         (.stop ^NanoHTTPD server#)))))

(deftest t-without-body
  (with-socket-server client
    (doseq [method [:get :delete]
            :let [request {:method method, :client client}]]
      (testing (name method)
        (testing "- OK"
          (let [{:keys [status body]}
                (http/request (merge {:url "/ok"} request))]
            (is (= 200 status))
            (is (= "OK" body)))
          (let [{:keys [status body]}
                (http/request (merge {:url "/ok", :as :stream} request))]
            (is (= 200 status))
            (is (= "OK" (slurp body)))))
        (testing "- FAIL"
          (let [{:keys [status body]}
                (http/request (merge {:url "/fail", :throw-exceptions false} request))]
            (is (= 500 status))
            (is (= "FAIL" body)))
          (is (thrown-with-msg?
                Exception
                #"HTTP Error: 500"
                (http/request (merge {:url "/fail"} request)))))))))

(deftest t-head-requests
  (with-socket-server client
    (testing "head"
      (let [{:keys [status body]}
            (http/request {:client client, :method :head, :url "/head"})]
        (is (= 200 status))
        (is (= "" body))))))

(deftest t-with-body
  (with-socket-server client
    (doseq [method [:post :put :patch :delete]
            :let [request {:method method
                           :client client
                           :body (str "DATA-" (rand-int 10000))}]]
      (testing (name method)
        (testing "- OK"
          (let [{:keys [status body]}
                (http/request (merge {:url "/echo"} request))]
            (is (= 200 status))
            (is (= (:body request) body)))
          (let [{:keys [status body]}
                (http/request (merge {:url "/echo", :as :stream} request))]
            (is (= 200 status))
            (is (= (:body request) (slurp body)))))
        (testing "- FAIL"
          (let [{:keys [status body]}
                (http/request (merge {:url "/fail", :throw-exceptions false} request))]
            (is (= 500 status))
            (is (= "FAIL" body)))
          (is (thrown-with-msg?
                Exception
                #"HTTP Error: 500"
                (http/request (merge {:url "/fail"} request)))))))))
