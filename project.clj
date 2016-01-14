(defproject org.onyxplatform/onyx-dashboard "0.8.4.0"
  :description "Dashboard for the Onyx distributed computation system"
  :url "http://github.com/lbradstreet/onyx-dashboard"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["spec/clj" "test"]

  :java-opts ["-Xmx2g" "-server"]

  :main onyx-dashboard.system

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308"]
                 [org.clojure/core.async "0.2.374"]
                 [prismatic/schema "0.4.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [com.taoensso/sente "1.5.0" :exclusions [com.taoensso/timbre com.taoensso/encore]]
                 ;[com.taoensso/timbre "4.1.2"]
                 [cljs-uuid "0.0.4"]
                 [ring "1.3.2"]
                 ^{:voom {:repo "git@github.com:onyx-platform/onyx.git" :branch "master"}}
                 [org.onyxplatform/onyx "0.8.4"]
                 [com.cognitect/transit-clj "0.8.275"]
                 [com.cognitect/transit-cljs "0.8.220"]
                 [cljsjs/moment "2.9.0-0"]
                 [ring/ring-defaults "0.1.5"]
                 [compojure "1.3.4"]
                 ;; Fixme, need to pin instaparse for some reason
                 ;; deps :tree says that compojure is bringing a compatible version
                 ;; in and I can't figure it out
                 [instaparse "1.4.1"]
                 [enlive "1.1.5"]
                 [fence "0.2.0"]
                 [fipp "0.6.2"]
                 [environ "1.0.0"]
                 [http-kit "2.1.19"]

                 ; make this explicit to fix uberjar?
                 [potemkin "0.3.13"]

                 [org.clojure/core.cache "0.6.4"]
                 [shoreleave/shoreleave-browser "0.3.0"]
                 [org.omcljs/om "0.8.8"]
                 [ankha "0.1.5.1-479897" :exclusions [om com.cemerick/austin]]
                 [racehub/om-bootstrap "0.5.1" :exclusions [om]]
                 [prismatic/om-tools "0.3.11" :exclusions [om]]]

  :plugins [[lein-cljsbuild "1.0.6"]
            ;[lein-version-spec "0.0.4"]
            [lein-environ "1.0.0"]]

  :min-lein-version "2.5.0"

  :uberjar-name "onyx-dashboard.jar"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :source-map "resources/public/js/app.map"
                                        :main onyx-dashboard.dev
                                        :asset-path "js/out"
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :clean-targets ^{:protect false} ["resources/public/js/advanced" 
                                    "resources/public/js/out" 
                                    "resources/public/js/app.js" 
                                    "target"]
  
  :hooks [leiningen.cljsbuild]

  :profiles {:dev {:source-paths ["env/dev/clj"]

                   :dependencies [[figwheel "0.3.3"]
                                  [clj-webdriver "0.6.1"]
                                  ;[com.cemerick/piggieback "0.2.1"]
                                  ;[weasel "0.5.0"]
                                  [leiningen "2.5.1"]]

                   :repl-options {:init-ns onyx-dashboard.system
                                  :timeout 90000
                                  ;:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]
                                  }

                   :plugins [[lein-figwheel "0.3.3"]
                             [lein-set-version "0.4.1"]
                             [lein-update-dependency "0.1.2"]
                             [lein-pprint "1.1.1"]
                             [lein-project-version "0.1.0"]]

                   :figwheel {:http-server-root "public"
                              :server-port 3428
                              :css-dirs ["resources/public/css"]}

                   :env {:peer-config "peer-config.edn"
                         :is-dev true}

                   :cljsbuild {:test-commands {}
                               :builds
                               {:app
                                {:source-paths ["env/dev/cljs"]}}}}

             :uberjar {:source-paths ["env/prod/clj"]
                       :hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :cljsbuild {:builds 
                                   {:uberjar {:source-paths ["src/cljs" "env/prod/cljs"]
                                              :compiler {:output-to "resources/public/js/app.js"
                                                         :output-dir "resources/public/js/advanced"
                                                         :source-map "resources/public/js/app.js.map"
                                                         :optimizations :advanced
                                                         :pretty-print false}}}}}})
