(ns unixsocket-http.core-test
  (:require [clojure.test.check
             [clojure-test :refer :all]
             [generators :as gen]
             [properties :as prop]]
            [com.gfredericks.test.chuck :refer [times]]
            [clojure.test :refer :all]
            [clojure.java.io :as io]
            [unixsocket-http.nanohttpd :refer :all]
            [unixsocket-http.core :as http]))

;; ## Test Setup
;;
;; Runs all test cases automatically against UNIX/TCP/... endpoints.

(def ^:dynamic make-client nil)

(use-fixtures
  :each
  (fn [f]
    (doseq [server-fn [create-unix-socket-server
                       create-tcp-socket-server]
            :let [{:keys [url stop]} (server-fn)]]
      (try
        (binding [make-client #(http/client url)]
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
           :post identity}])
       (gen/fmap
         (fn [{:keys [pre post]}]
           (comp post http/request pre)))))

(defn- gen-request
  [gen]
  (gen/fmap
    (fn [[req headers query-params]]
      (assoc req
             :headers headers
             :query-params query-params))
    (gen/tuple
      gen
      (gen/map
        (gen/elements ["x-header-one" "x-header-two"])
        gen/string-ascii)
      (gen/map
        (gen/fmap str gen/char-alpha)
        gen/string-ascii))))

(defn- gen-echo-request
  []
  (->> (gen/tuple
         (gen/elements [:post :put :patch :delete])
         gen/string-ascii)
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
    (let [{:keys [status body]} (send! request)]
      (and (= 500 status) (= "FAIL" body)))))
