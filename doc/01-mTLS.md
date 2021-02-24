# mTLS

While TLS is supported out-of-the-box, as long as the relevant certificates are
part of the Java keystore/truststore, **mutual TLS (mTLS)** requires explicit
configuration.

Since the underlying client is provided by [`okhttp`][okhttp] we'll rely on
[`okhttp-tls`][okhttp-tls] to set up the communication.

[okhttp]: https://square.github.io/okhttp/
[okhttp-tls]: https://square.github.io/okhttp/4.x/okhttp-tls/okhttp3.tls/

## Keys & Certificate

We need a `java.security.KeyPair` (private key) and
`java.security.cert.X509Certificate` instances (public key and certificate
authority) to create the underlying `SslSocketFactory`.

Depending on the input formats
available you might be able to use built-in Java facilities to obtain these;
alternatively, [BouncyCastle][bc] or [pem-reader][] provide the desired
functionality.

[bc]: https://www.bouncycastle.org/docs/pkixdocs1.5on/org/bouncycastle/openssl/PEMParser.html
[pem-reader]: https://github.com/into-docker/pem-reader

## Client Creation

### Imports

```clojure
(import '(okhttp3.tls
          HandshakeCertificates
          HandshakeCertificates$Builder
          HeldCertificate)
        '(java.security KeyPair)
        '(java.security.cert X509Certificate))
```

### HandshakeCertificates

The class `okhttp3.tls.HandshakeCertificates` encapsulates all the data
exchanged during connection setup.

```clojure
(defn handshake-certificates
  ^HandshakeCertificates
  [^KeyPair         key
   ^X509Certificate cert
   ^X509Certificate ca]
  (-> (HandshakeCertificates$Builder.)
      (.addTrustedCertificate ca)
      (.heldCertificate
       (HeldCertificate. key cert)
       (into-array X509Certificate []))
      (.build)))
```

### Create `:builder-fn`

Once the `HandshakeCertificates` are ready, you can use them to retrieve an
`SslSocketFactory` and `TrustManager` and pass them to every created client:

```clojure
(defn mtls-builder-fn
  [key cert ca]
  (let [hs (handshake-certificates key cert ca)]
    (fn [^okhttp3.OkHttpClient$Builder builder]
      (doto builder
        (.sslSocketFactory
         (.sslSocketFactory hs)
         (.trustManager hs))))))
```

### Create client

Note that you'll still have to supply a `https://...` URL for the certificates
to actually be picked up.

```clojure
(require '[unixsocket-http.core :as uhttp])
(let [client (uhttp/client
               "https://..."
               {:builder-fn (mtls-builder-fn key cert ca)})]
  (uhttp/get client "/_ping"))
```
