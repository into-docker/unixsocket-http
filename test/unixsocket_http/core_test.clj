(ns unixsocket-http.core-test
  (:require [clojure.test.check
             [clojure-test :refer :all]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [unixsocket-http.test.http-server :refer :all]
            [unixsocket-http.core :as http]))

;; ## Test Setup
;;
;; Runs all test cases automatically against UNIX/TCP/... endpoints.

(def ^:dynamic make-client nil)
(def ^:dynamic adjust-url identity)

(use-fixtures
  :each
  (fn [f]
    (doseq [server-fn [create-unix-socket-server
                       create-tcp-socket-server
                       create-http-socket-server
                       create-https-socket-server]
            :let [{:keys [^String url stop opts]} (server-fn)]]
      (try
        (binding [make-client #(http/client url opts)
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
         [{:pre #(assoc % :as :stream)
           :post #(update % :body slurp)}
          {:pre identity
           :post identity}
          {:pre #(update % :url adjust-url)
           :post identity}])
       (gen/fmap
         (fn [{:keys [pre post]}]
           (comp post http/request pre)))))

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
        gen/string-ascii)
      (gen/map
        (gen/fmap str gen/char-alpha)
        gen/string-ascii))))


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
  []
  (->> (gen/elements [:get :post :put :patch :delete])
       (gen/fmap
         (fn [method]
           {:client (make-client)
            :method method
            :url    "/fail"}))
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

(defspec t-failing-request (times 50)
  (prop/for-all
    [request (gen-fail-request)
     send!   (gen-request-fn)]
    (and (is (thrown-with-msg?
               Exception
               #"HTTP Error: 500"
               (send! request)))
         true)))

(defspec t-failing-request-without-exception (times 50)
  (prop/for-all
    [request (->> (gen-fail-request)
                  (gen/fmap #(assoc % :throw-exceptions false)))
     send!   (gen-request-fn)]
    (let [{:keys [status body] :as resp} (send! request)]
      (and (= 500 status) (= "FAIL" body)))))

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
