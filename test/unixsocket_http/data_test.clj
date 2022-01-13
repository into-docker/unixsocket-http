(ns unixsocket-http.data-test
  (:require [unixsocket-http.client :as client]
            [unixsocket-http.data :as data]
            [clojure.test :refer [deftest testing is]]))

(def ^:private url
  "http://areiotnaiotrsanitosrna:12345")

(def ^:private client
  {::client/factory  (constantly nil)
   ::client/base-url url})

(deftest t-build-request
  (testing "request without scheme/hostname"
    (let [request (data/build-request
                    {:client client
                     :method :get
                     :url    "/"})]
      (is (= "GET" (.method request)))
      (is (= (str url "/") (str (.url request))))
      (is (nil? (.body request)))))

  (testing "request with scheme/hostname"
    (let [url'    "http://oneiontgsreionnehbfei:4344/test"
          request (data/build-request
                    {:client client
                     :method :get
                     :url    url'})]
      (is (= "GET" (.method request)))
      (is (= url' (str (.url request))))
      (is (nil? (.body request)))))

  (testing "request with query parameters"
    (let [request (data/build-request
                    {:client       client
                     :method       :get
                     :query-params {:x 1, :y "2"}
                     :url          url})]
      (is (= "GET" (.method request)))
      (is (= (str url "/?x=1&y=2") (str (.url request))))
      (is (nil? (.body request)))))

  (testing "request with repeating query parameters"
    (let [request (data/build-request
                    {:client       client
                     :method       :get
                     :query-params {:x [1 2 3]}
                     :url          url})]
      (is (= "GET" (.method request)))
      (is (= (str url "/?x=1&x=2&x=3") (str (.url request))))
      (is (nil? (.body request)))))

  (testing "request with headers"
    (let [request (data/build-request
                    {:client  client
                     :method  :get
                     :headers {"X-Custom" "1"}
                     :url     url})]
      (is (= "GET" (.method request)))
      (is (= (str url "/") (str (.url request))))
      (is (= {"x-custom" ["1"]} (.toMultimap (.headers request))))
      (is (nil? (.body request))))))
