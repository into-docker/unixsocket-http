(ns unixsocket-http.core-test
  (:require [clojure.test.check
             [clojure-test :refer [defspec]]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.test :refer [is use-fixtures]]
            [clojure.java.io :as io]
            [unixsocket-http.test.http-server :as httpd]
            [unixsocket-http.core :as http]))

;; ## Test Setup
;;
;; Runs all test cases automatically against UNIX/TCP/... endpoints.

(def ^:dynamic make-client nil)
(def ^:dynamic adjust-url identity)

(use-fixtures
  :each
  (fn [f]
    (doseq [server-fn [httpd/create-unix-socket-server
                       httpd/create-tcp-socket-server
                       httpd/create-http-socket-server
                       httpd/create-https-socket-server]
            :let [{:keys [^String url stop opts]} (server-fn)]]
      (try
        (binding [make-client (if opts
                                #(http/client url opts)
                                #(http/client url))
                  adjust-url  (if (.startsWith url "http")
                                #(str url %)
                                #(str "http://localhost" %))]
          (f))
        (finally
          (stop))))))

;; ## Request Generators

(defn- gen-request-fn
  "This allows us to easily test e.g. `:as` specifiers in requests."
  []
  (->> (gen/elements
         [;; response as stream
          {:pre #(assoc % :as :stream)
           :post #(update % :body slurp)}

          ;; request as stream
          {:pre #(update %
                         :body
                         (fn [data]
                           (some-> ^String data
                                   (.getBytes "UTF-8")
                                   (java.io.ByteArrayInputStream.))))
           :post identity}

          ;; as-is
          {:pre identity
           :post identity}

          ;; use full url
          {:pre #(update % :url adjust-url)
           :post identity}])
       (gen/fmap
         (fn [{:keys [pre post]}]
           (comp post http/request pre)))))

(defn- gen-socket-request-fn
  []
  (->> (gen/elements
         [;; response as socket
          {:pre #(assoc % :as :socket)
           :post identity}])
       (gen/fmap
         (fn [{:keys [pre post]}]
           (comp post http/request pre)))))

(defn- gen-any-request-fn
  []
  (gen/one-of [(gen-socket-request-fn)
               (gen-request-fn)]))

(defn- gen-request
  [gen]
  (gen/fmap
    (fn [[req headers query-params]]
      (-> req
          (update :headers #(merge headers %))
          (update :query-params #(merge query-params %))))
    (gen/tuple
      gen
      (gen/map
        (gen/elements ["x-header-one" "x-header-two"])
        gen/string-ascii
        {:min-elements 0, :max-elements 2})
      (gen/map
        (gen/fmap str gen/char-alpha)
        gen/string-ascii
        {:min-elements 0, :max-elements 3}))))

(defn- gen-stream-request
  []
  (->> (gen/tuple
         (gen/elements [:post :put :patch :delete])
         gen/string-ascii)
       (gen/fmap
         (fn [[method data]]
           {:client  (make-client)
            :method  method
            :url     "/stream"
            :data    data
            :as      :socket}))
       (gen-request)))

(defn- gen-echo-request
  []
  (->> (gen/tuple
         (gen/elements [:post :put :patch :delete])
         (gen/such-that seq gen/string-ascii))
       (gen/fmap
         (fn [[method body]]
           {:client (make-client)
            :method method
            :url    "/echo"
            :body   body}))
       (gen-request)))

(defn- gen-ok-request
  []
  (->> (gen/elements [:get :post :put :patch :delete])
       (gen/fmap
         (fn [method]
           {:client (make-client)
            :method method
            :url    "/ok"}))
       (gen-request)))

(defn- gen-head-request
  []
  (->> (gen/elements [:head])
       (gen/fmap
         (fn [method]
           {:client (make-client)
            :method method
            :url    "/head"}))
       (gen-request)))

(defn- gen-fail-request
  [& [opts]]
  (->> (gen/elements [:get :post :put :patch :delete])
       (gen/fmap
         (fn [method]
           (merge
             {:client (make-client)
              :method method
              :url    "/fail"}
             opts)))
       (gen-request)))

;; ## Tests

(defspec t-request-with-body (times 50)
  (prop/for-all
    [request (gen-echo-request)
     send!   (gen-request-fn)]
    (let [{:keys [status body]} (send! request)]
      (and (= 200 status) (= (:body request) body)))))

(defspec t-request-without-body (times 50)
  (prop/for-all
    [request (gen-ok-request)
     send!   (gen-request-fn)]
    (let [{:keys [status body]} (send! request)]
      (and (= 200 status) (= "OK" body)))))

(defspec t-head-request (times 5)
  (prop/for-all
    [request (gen-head-request)
     send!   (gen-request-fn)]
    (let [{:keys [status body]} (send! request)]
      (and (= 200 status) (= "" body)))))

(defspec t-request-with-socket (times 10)
  (prop/for-all
    [request (gen-ok-request)
     send!   (gen-socket-request-fn)]
    (let [{:keys [status body]} (send! request)]
      (with-open [^java.net.Socket socket body]
        (and (instance? java.net.Socket socket)
             (= 200 status))))))

(defspec t-failing-request (times 50)
  (prop/for-all
    [request (gen-fail-request)
     send!   (gen-any-request-fn)]
    (and (is (thrown-with-msg?
               Exception
               #"HTTP Error: 500"
               (send! request)))
         true)))

(defspec t-failing-request-without-body (times 10)
  (prop/for-all
    [request (gen-fail-request {:as :stream})
     send!   (gen-request-fn)]
    (try
      (send! request)
      false
      (catch clojure.lang.ExceptionInfo e
        (let [{:keys [status body]} (ex-data e)]
          (and (= 500 status)
               (nil? body)))))))

(defspec t-failing-request-with-body (times 10)
  (prop/for-all
    [request (gen-fail-request
               {:as :stream
                :throw-entire-message? true})
     send!   (gen-request-fn)]
    (try
      (send! request)
      false
      (catch clojure.lang.ExceptionInfo e
        (let [{:keys [status body]} (ex-data e)]
          (and (= 500 status)
               (= "FAIL" (slurp body))))))))

(defspec t-failing-request-without-exception (times 50)
  (prop/for-all
    [request (gen/let [req (gen-fail-request)
                       k   (gen/elements [:throw-exceptions :throw-exceptions?])
                       v   (gen/elements [nil false])]
               (assoc req k v))
     send!   (gen-request-fn)]
    (let [{:keys [status body]} (send! request)]
      (and (= 500 status) (= "FAIL" body)))))

(defspec t-invalid-method (times 5)
  (prop/for-all
    [request (->> (gen-fail-request)
                  (gen/fmap #(assoc % :method :unknown)))
     send!   (gen-any-request-fn)]
    (and (is (thrown-with-msg?
               Exception
               #"Invalid HTTP method keyword supplied: :unknown"
               (send! request)))
         true)))

(defspec t-invalid-as-statements (times 5)
  (prop/for-all
    [request (->> (gen-ok-request)
                  (gen/fmap #(assoc % :as :unknown)))]
    (and (is (thrown?
               IllegalArgumentException
               (http/request request)))
         true)))

(defspec t-http-wrappers (times 50)
  (prop/for-all
    [request (gen-ok-request)]
    (let [{:keys [client method url]} request
          opts (dissoc request :client :method :url)
          http-fn (case method
                    :get    http/get
                    :post   http/post
                    :put    http/put
                    :delete http/delete
                    :patch  http/patch)]
      (if (empty? opts)
        (http-fn client url)
        (http-fn client url opts)))))

(comment
  ;; This cannot be verified using the MockWebServer, unfortunately.
  (defspec t-bidirectional-request (times 50)
    (prop/for-all
      [{:keys [data] :as request} (gen-stream-request)]
      (let [{:keys [status body]} (http/request request)]
        (with-open [^java.net.Socket socket body
                    out (.getOutputStream socket)
                    in (.getInputStream socket)]
          (io/copy data out)
          (and (= 200 status)
               (= data (slurp in))))))))
