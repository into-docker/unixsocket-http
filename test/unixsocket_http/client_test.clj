(ns unixsocket-http.client-test
  (:require [unixsocket-http.client :as client]
            [clojure.test :refer [deftest testing is]])
  (:import [okhttp3 OkHttpClient]
           [java.net Socket]))

(def ^:private url
  "http://areiotnaiotrsanitosrna:12345")

(deftest t-client-and-connection
  (testing "default client"
    (let [client {:client (client/create url {})}]
      (is (= url (client/base-url client)))
      (testing "- default request"
        (let [connection (client/connection client)]
          (is (instance? okhttp3.OkHttpClient (client/get-client connection)))
          (is (nil? (client/get-socket connection)))))
      (testing "- socket request"
        (let [connection (client/connection (merge {:as :socket} client))]
          (is (instance? OkHttpClient (client/get-client connection)))
          (is (instance? Socket (client/get-socket connection)))))))

  (testing "recreating client"
    (let [client {:client (client/create url {:mode :recreate})}]
      (is (= url (client/base-url client)))
      (testing "- default request"
        (let [connection (client/connection client)]
          (is (instance? okhttp3.OkHttpClient (client/get-client connection)))
          (is (nil? (client/get-socket connection)))))
      (testing "- socket request"
        (let [connection (client/connection (merge {:as :socket} client))]
          (is (instance? OkHttpClient (client/get-client connection)))
          (is (instance? Socket (client/get-socket connection)))))))

  (testing "reusable client"
    (let [client {:client (client/create url {:mode :reuse})}]
      (is (= url (client/base-url client)))
      (testing "- default request"
        (let [connection (client/connection client)]
          (is (instance? okhttp3.OkHttpClient (client/get-client connection)))
          (is (nil? (client/get-socket connection)))))
      (testing "- socket request"
        (is (thrown-with-msg?
              IllegalArgumentException
              #"Client mode `:reuse` does not allow direct socket access"
              (client/connection (merge {:as :socket} client))))))))

(deftest t-client-with-base-url
  (doseq [mode [:default :reuse :recreate]]
    (let [base-url (str url "/base")
          client {:client (client/create url {:mode mode, :base-url base-url})}]
      (is (= base-url (client/base-url client))))))
