(ns simple-ring
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [clojure.java.shell :refer [sh]]))

(defn token-auth [endpoint headers]
  (cond (contains? endpoint :gitlab-token)
        (let [gitlab-token (get endpoint :gitlab-token)]
          (= gitlab-token (get headers "x-gitlab-token")))
        (contains? endpoint :github-signature)
        (let [github-signature (get endpoint :github-signature)]
          ;; signature digest missing!
          (= github-signature (get headers "x-hub-signature")))
        :else true))

(defn create-handler [endpoints]
  (fn [request respond raise]
    (if-let [endpoint (get endpoints (:uri request))]
      (if-not (token-auth endpoint (:headers request))
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
        handler                       (create-handler endpoints)]
    (run-jetty handler {:port   (or port 1988)
                        :async? true
                        :host   (or host "127.0.0.1")})))

