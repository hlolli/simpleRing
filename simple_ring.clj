(ns simple-ring
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-body]]
            [pandect.algo.sha1 :as sha1]
            [crypto.equality]
            [clojure.java.shell :refer [sh]]))

(defn wrap-payload [handler]
  (fn [request respond raise]
    (let [updated-request (assoc request :payload (slurp (:body request)))]
      (handler updated-request respond raise))))

(defn token-auth [endpoint headers payload]
  (cond (and (contains? endpoint :gitlab-token)
             (get headers "x-gitlab-token"))
        (let [gitlab-token (get endpoint :gitlab-token)]
          (= gitlab-token (get headers "x-gitlab-token")))
        (and (contains? endpoint :github-secret)
             (get headers "x-hub-signature"))
        (let [github-secret     (get endpoint :github-secret)
              ;; from https://github.com/dryewo/clj-github-app
              payload-signature (str "sha1=" (sha1/sha1-hmac payload github-secret))]
          (crypto.equality/eq? payload-signature (get headers "x-hub-signature")))
        :else false))

(defn create-handler [endpoints]
  (fn [request respond raise]
    (if-let [endpoint (get endpoints (:uri request))]
      (if-not (token-auth endpoint (:headers request) (:payload request))
        (respond {:status 500 :body "Authorization failed!"})
        (do (respond {:status 200 :body (or (:response-body endpoint) "")})
            (when-let [script (:script endpoint)]
              (let  [{:keys [exit out err]} (sh "sh" "-c" script)]
                (println "script exit code: " exit)
                (when-not (empty? out) (println "STDOUT: " out))
                (when-not (empty? err) (println "STDERR: " err))))
            (when-let [shell (:shell endpoint)]
              (let  [{:keys [exit out err]} (sh shell)]
                (println "shell script exit code: " exit)
                (when-not (empty? out) (println "STDOUT: " out))
                (when-not (empty? err) (println "STDERR: " err))))))
      (respond {:status 404}))))

(defn -main [config-path]
  (assert (not (empty? config-path)) "You must provide path to edn config file!")
  (let [{:keys [port host endpoints]} (read-string (slurp config-path))
        handler                       (-> (create-handler endpoints)
                                          (wrap-json-body {:keywords? true})
                                          (wrap-payload))]
    (run-jetty handler {:port   (or port 1988)
                        :async? true
                        :host   (or host "127.0.0.1")})))
