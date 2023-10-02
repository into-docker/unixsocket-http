(defproject unixsocket-http "1.0.13"
  :description "A library to allow HTTP calls over a UNIX socket, e.g. for
                communicating with Docker."
  :url "https://github.com/into-docker/unixsocket-http"
  :license {:name "MIT"
            :url "https://choosealicense.com/licenses/mit"
            :year 2020
            :key "mit"
            :comment "MIT License"}
  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [org.clojure/tools.logging "1.2.4"]
                 [com.kohlschutter.junixsocket/junixsocket-core "2.8.0" :extension "pom"]
                 [com.squareup.okhttp3/okhttp "4.11.0"]
                 [org.jetbrains.kotlin/kotlin-stdlib-common "1.9.10"]]
  :exclusions [org.clojure/clojure]
  :aot [unixsocket-http.impl.FixedPathUnixSocket
        unixsocket-http.impl.FixedPathUnixSocketFactory
        unixsocket-http.impl.FixedPathTcpSocket
        unixsocket-http.impl.FixedPathTcpSocketFactory
        unixsocket-http.impl.SingletonSocketFactory
        unixsocket-http.impl.ResponseSocket
        unixsocket-http.impl.StreamingBody]
  :profiles {:dev
             {:dependencies [[com.squareup.okhttp3/okhttp-tls "4.11.0"]
                             [com.squareup.okhttp3/mockwebserver "4.11.0"]
                             [org.clojure/test.check "1.1.1"]
                             [com.gfredericks/test.chuck "0.2.14"]]
              :global-vars {*warn-on-reflection* true}}
             :kaocha
             {:dependencies [[lambdaisland/kaocha "1.87.1366"
                              :exclusions [org.clojure/spec.alpha]]
                             [lambdaisland/kaocha-cloverage "1.1.89"]
                             [org.clojure/java.classpath "1.0.0"]]}
             :ci
             [:kaocha
              {:global-vars {*warn-on-reflection* false}}]}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner"]
            "ci"     ["with-profile" "+ci" "run" "-m" "kaocha.runner"
                      "--reporter" "documentation"
                      "--plugin"   "cloverage"
                      "--codecov"
                      "--no-cov-html"
                      "--cov-ns-exclude-regex" "unixsocket-http\\.impl\\..+"]}
  :pedantic? :abort)
