(ns unixsocket-http.tls
  (:require [pem-reader.core :as pem]
            [unixsocket-http.core :as uhttp])
  (:import (okhttp3.tls
            HandshakeCertificates
            HandshakeCertificates$Builder
            HeldCertificate)
           (java.security KeyPair)
           (java.security.cert X509Certificate)))

;; ## Helpers

(defn- held-certificate
  ^HeldCertificate [key-file cert-file]
  (HeldCertificate.
   (pem/read-key-pair key-file)
   (pem/read-certificate cert-file)))

;; ## HandshakeCertificates

(defn handshake-certificates
  ^HandshakeCertificates [{:keys [key ca cert]}]
  (-> (HandshakeCertificates$Builder.)
      (.addTrustedCertificate (pem/read-certificate ca))
      (.heldCertificate
       (held-certificate key cert)
       (into-array X509Certificate []))
      (.build)))

;; ## Builder Function

(defn mtls-builder-fn
  [opts]
  (let [hs (handshake-certificates opts)]
    (fn [^okhttp3.OkHttpClient$Builder builder]
      (doto builder
        (.sslSocketFactory
         (.sslSocketFactory hs)
         (.trustManager hs))))))

(comment
  (let [client (->> {:builder-fn (mtls-builder-fn
                                  {:key  "certs/client/key.pem"
                                   :cert "certs/client/cert.pem"
                                   :ca   "certs/client/ca.pem"})}
                    (uhttp/client "https://localhost:55000"))]
    (uhttp/get client "/_ping")))
