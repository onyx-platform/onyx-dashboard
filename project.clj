(defproject onyx-dashboard "0.5.3.2-SNAPSHOT"
  :description "Dashboard for the Onyx distributed computation system"
  :url "http://github.com/lbradstreet/onyx-dashboard"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj"]

  :test-paths ["spec/clj"]

  :java-opts ["-Xmx2g" "-server"]

  :main onyx-dashboard.system

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2816"]
                 [prismatic/schema "0.3.7"]
                 [com.stuartsierra/component "0.2.2"]
                 [com.taoensso/sente "1.3.0"]
                 [com.taoensso/timbre "3.3.1"]
                 [cljs-uuid "0.0.4"]
                 [ring "1.3.2"]
                 [com.mdrogalis/onyx "0.5.3"]
                 [com.cognitect/transit-clj "0.8.259"]
                 [com.cognitect/transit-cljs "0.8.205"]
                 [cljsjs/moment "2.9.0-0"]
                 [ring/ring-defaults "0.1.3"]
                 [compojure "1.3.1"]
                 [enlive "1.1.5"]
                 [fence "0.2.0"]
                 [fipp "0.5.2"]
                 [environ "1.0.0"]
                 [http-kit "2.1.19"]
                 [shoreleave/shoreleave-browser "0.3.0"]
                 ; make this explicit to fix uberjar?
                 [potemkin "0.3.11"]
                 [org.omcljs/om "0.8.8"]
                 [ankha "0.1.5.1-479897" :exclude [om]]
                 [racehub/om-bootstrap "0.4.0" :exclusions [om]]
                 [prismatic/om-tools "0.3.10" :exclusions [om]]]

  :plugins [[lein-cljsbuild "1.0.4"]
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

  :profiles {:dev {:source-paths ["env/dev/clj"]

                   :dependencies [[figwheel "0.2.3-SNAPSHOT"]
                                  [com.cemerick/piggieback "0.1.5"]
                                  ;[weasel "0.5.0"]
                                  [leiningen "2.5.1"]]

                   :repl-options {:init-ns onyx-dashboard.system
                                  :timeout 90000
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :plugins [[lein-figwheel "0.2.3-SNAPSHOT"]]

                   :figwheel {:http-server-root "public"
                              :server-port 3428
                              :css-dirs ["resources/public/css"]}

                   :env {:is-dev true}

                   :cljsbuild {:builds
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
