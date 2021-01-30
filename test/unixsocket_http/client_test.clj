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
  (testing "uses custom base URL"
    (doseq [mode [:default :reuse :recreate]]
      (let [base-url (str url "/base")
            client {:client (client/create url {:mode mode, :base-url base-url})}]
        (is (= base-url (client/base-url client))))))
  (testing "removes trailing slash"
    (let [url'   (str url "/")
          client {:client (client/create url' {})}]
      (is (= url (client/base-url client)))))
  (testing "introduces 'unix://' prefix (deprecated behaviour)"
    (let [path "/var/sock/docker.sock"
          client {:client (client/create path {})}]
      (is (= "http://localhost"
             (client/base-url client))))))

(deftest t-client-with-timeouts
  (testing "default timeout"
    (let [client     {:client (client/create url {})}
          oclient    (-> (client/connection client) (client/get-client))]
      (is (= 0 (.callTimeoutMillis oclient)))
      (is (= 0 (.connectTimeoutMillis oclient)))
      (is (= 0 (.readTimeoutMillis oclient)))))
  (testing "general timeout"
    (let [timeout-ms 100
          client     {:client (client/create url {:timeout-ms timeout-ms})}
          oclient    (-> (client/connection client) (client/get-client))]
      (is (= timeout-ms (.callTimeoutMillis oclient)))
      (is (= timeout-ms (.connectTimeoutMillis oclient)))
      (is (= timeout-ms (.readTimeoutMillis oclient)))))
  (testing "single timeouts"
    (let [client     {:client (client/create url {:call-timeout-ms    100
                                                  :connect-timeout-ms 101
                                                  :read-timeout-ms    102})}
          oclient    (-> (client/connection client) (client/get-client))]
      (is (= 100 (.callTimeoutMillis oclient)))
      (is (= 101 (.connectTimeoutMillis oclient)))
      (is (= 102 (.readTimeoutMillis oclient))))))

(deftest t-client-with-invalid-mode
  (is (thrown?
        IllegalArgumentException
        (client/create url {:mode :unknown}))))

(deftest t-client-with-invalid-url
  (testing "invalid scheme"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"Can only handle URI schemes .+, given: .+"
          (client/create "unknown://host" {}))))
  (testing "TCP URL without port"
    (is (thrown-with-msg?
          IllegalArgumentException
          #"Port is required in URI: tcp://.+"
          (client/create "tcp://12.1.1.1" {})))))
