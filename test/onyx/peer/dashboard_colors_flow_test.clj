(ns onyx.peer.dashboard-colors-flow-test
  (:require [clojure.core.async :refer [>!! chan close! sliding-buffer]]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [etaoin.api :refer :all]
            [onyx-dashboard.system :as sys]
            onyx.api
            [onyx.plugin.core-async :refer [take-segments!]]
            onyx.test-helper))

(defn load-last-deployment [driver]
  (wait-visible driver {:tag :div :class :container})
  (click driver {:tag :button :id :ddl-deployment})
  (click-el driver (first (query-all driver {:tag :a})))
  (click driver {:css "li:last-child"}))

(defn load-job [driver]
  (wait-predicate #(= 2
                      (count (query-all driver {:css "tr.job-entry"}))))
  (click driver (second (query-all driver {:css "tr.job-entry"}))))

(defn check-job-text [driver workflow]
  ;; remove this sleep - should perform a wait-until
  (Thread/sleep 4000)
  (let [[workflow-text catalog-text lifecycles-text]
        (map #(query driver %) (query-all driver {:css "div.ace_content"}))]
    (is (= (clojure.string/replace workflow-text "\n" "")
           (str workflow)))

    (is (not (empty? catalog-text)))
    (is (not (empty? lifecycles-text)))))

(defn initialize-driver
  [browser-type]
  (case browser-type
    :chrome (chrome)
    :chrome-headless (chrome-headless)
    :firefox (firefox)
    :firefox-headless (firefox-headless)
    :phantom (phantom)
    :safari (safari)
    (chrome)))

(deftest load-site
  (testing "Load site and run checks"

    (def id (java.util.UUID/randomUUID))

    (defn run-test-fixture
      [browser-type f]
      (let [system (component/start (sys/get-system "127.0.0.1:2188" false))
            driver (initialize-driver browser-type)]
        (wait driver 20)
        (try
          (f driver)
          (finally
            (component/stop system)
            (quit driver)))))

    (def config
      {:env-config {:zookeeper/address "127.0.0.1:2188"
                    :zookeeper/server? true
                    :zookeeper.server/port 2188}
       :peer-config {:zookeeper/address "127.0.0.1:2188"
                     :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
                     :onyx.messaging/impl :aeron
                     :onyx.messaging/peer-port 40200
                     :onyx.messaging/bind-addr "localhost"}})

    (def env-config (assoc (:env-config config) :onyx/tenancy-id id))

    (def peer-config (assoc (:peer-config config) :onyx/tenancy-id id))

    (def env (onyx.api/start-env env-config))

    (def peer-group (onyx.api/start-peer-group peer-config))

    (def batch-size 10)

    (def colors-in-chan (chan 100))
    (def colors-in-buffer (atom {}))

    (def red-out-chan (chan (sliding-buffer 100)))

    (def blue-out-chan (chan (sliding-buffer 100)))

    (def green-out-chan (chan (sliding-buffer 100)))

    (doseq [x [{:color "red" :extra-key "Some extra context for the predicates"}
               {:color "blue" :extra-key "Some extra context for the predicates"}
               {:color "white" :extra-key "Some extra context for the predicates"}
               {:color "green" :extra-key "Some extra context for the predicates"}
               {:color "orange" :extra-key "Some extra context for the predicates"}
               {:color "black" :extra-key "Some extra context for the predicates"}
               {:color "purple" :extra-key "Some extra context for the predicates"}
               {:color "cyan" :extra-key "Some extra context for the predicates"}
               {:color "yellow" :extra-key "Some extra context for the predicates"}]]
      (>!! colors-in-chan x))

    (def catalog
      [{:onyx/name :colors-in
        :onyx/plugin :onyx.plugin.core-async/input
        :onyx/type :input
        :onyx/medium :core.async
        :onyx/batch-size batch-size
        :onyx/max-peers 1
        :onyx/doc "Reads segments from a core.async channel"}

       {:onyx/name :process-red
        :onyx/fn :onyx.peer.dashboard-colors-flow-test/process-red
        :onyx/type :function
        :onyx/batch-size batch-size}

       {:onyx/name :process-blue
        :onyx/fn :onyx.peer.dashboard-colors-flow-test/process-blue
        :onyx/type :function
        :onyx/batch-size batch-size}

       {:onyx/name :process-green
        :onyx/fn :onyx.peer.dashboard-colors-flow-test/process-green
        :onyx/type :function
        :onyx/batch-size batch-size}

       {:onyx/name :red-out
        :onyx/plugin :onyx.plugin.core-async/output
        :onyx/type :output
        :onyx/medium :core.async
        :onyx/batch-size batch-size
        :onyx/max-peers 1
        :onyx/doc "Writes segments to a core.async channel"}

       {:onyx/name :blue-out
        :onyx/plugin :onyx.plugin.core-async/output
        :onyx/type :output
        :onyx/medium :core.async
        :onyx/batch-size batch-size
        :onyx/max-peers 1
        :onyx/doc "Writes segments to a core.async channel"}

       {:onyx/name :green-out
        :onyx/plugin :onyx.plugin.core-async/output
        :onyx/type :output
        :onyx/medium :core.async
        :onyx/batch-size batch-size
        :onyx/max-peers 1
        :onyx/doc "Writes segments to a core.async channel"}])

    (def workflow
      [[:colors-in :process-red]
       [:colors-in :process-blue]
       [:colors-in :process-green]

       [:process-red :red-out]
       [:process-blue :blue-out]
       [:process-green :green-out]])

    (def flow-conditions
      [{:flow/from :colors-in
        :flow/to :all
        :flow/short-circuit? true
        :flow/exclude-keys [:extra-key]
        :flow/predicate :onyx.peer.dashboard-colors-flow-test/white?}

       {:flow/from :colors-in
        :flow/to :none
        :flow/short-circuit? true
        :flow/exclude-keys [:extra-key]
        :flow/action :retry
        :flow/predicate :onyx.peer.dashboard-colors-flow-test/black?}

       {:flow/from :colors-in
        :flow/to [:process-red]
        :flow/short-circuit? true
        :flow/exclude-keys [:extra-key]
        :flow/predicate :onyx.peer.dashboard-colors-flow-test/red?}

       {:flow/from :colors-in
        :flow/to [:process-blue]
        :flow/short-circuit? true
        :flow/exclude-keys [:extra-key]
        :flow/predicate :onyx.peer.dashboard-colors-flow-test/blue?}

       {:flow/from :colors-in
        :flow/to [:process-green]
        :flow/short-circuit? true
        :flow/exclude-keys [:extra-key]
        :flow/predicate :onyx.peer.dashboard-colors-flow-test/green?}

       {:flow/from :colors-in
        :flow/to [:process-red]
        :flow/exclude-keys [:extra-key]
        :flow/predicate :onyx.peer.dashboard-colors-flow-test/orange?}

       {:flow/from :colors-in
        :flow/to [:process-blue]
        :flow/exclude-keys [:extra-key]
        :flow/predicate :onyx.peer.dashboard-colors-flow-test/orange?}

       {:flow/from :colors-in
        :flow/to [:process-green]
        :flow/exclude-keys [:extra-key]
        :flow/predicate :onyx.peer.dashboard-colors-flow-test/orange?}])

    (defn inject-colors-in-ch [event lifecycle]
      {:core.async/buffer colors-in-buffer
       :core.async/chan colors-in-chan})

    (defn inject-red-out-ch [event lifecycle]
      {:core.async/chan red-out-chan})

    (defn inject-blue-out-ch [event lifecycle]
      {:core.async/chan blue-out-chan})

    (defn inject-green-out-ch [event lifecycle]
      {:core.async/chan green-out-chan})

    (def colors-in-calls
      {:lifecycle/before-task-start inject-colors-in-ch})

    (def red-out-calls
      {:lifecycle/before-task-start inject-red-out-ch})

    (def blue-out-calls
      {:lifecycle/before-task-start inject-blue-out-ch})

    (def green-out-calls
      {:lifecycle/before-task-start inject-green-out-ch})

    (def lifecycles
      [{:lifecycle/task :colors-in
        :lifecycle/calls :onyx.peer.dashboard-colors-flow-test/colors-in-calls}
       {:lifecycle/task :red-out
        :lifecycle/calls :onyx.peer.dashboard-colors-flow-test/red-out-calls}
       {:lifecycle/task :blue-out
        :lifecycle/calls :onyx.peer.dashboard-colors-flow-test/blue-out-calls}
       {:lifecycle/task :green-out
        :lifecycle/calls :onyx.peer.dashboard-colors-flow-test/green-out-calls}])

    (def seen-before? (atom false))

    (defn black? [event old {:keys [color]} all-new]
      (if (and (not @seen-before?) (= color "black"))
        (do
          (swap! seen-before? (constantly true))
          true)
        false))

    (defn white? [event old {:keys [color]} all-new]
      (= color "white"))

    (defn red? [event old {:keys [color]} all-new]
      (= color "red"))

    (defn blue? [event old {:keys [color]} all-new]
      (= color "blue"))

    (defn green? [event old {:keys [color]} all-new]
      (= color "green"))

    (defn orange? [event old {:keys [color]} all-new]
      (= color "orange"))

    (def constantly-true (constantly true))

    (def process-red identity)

    (def process-blue identity)

    (def process-green identity)

    (def v-peers (onyx.api/start-peers 7 peer-group))

    (close! colors-in-chan)

    (->> (onyx.api/submit-job
          peer-config
          {:catalog catalog :workflow workflow
           :flow-conditions flow-conditions :lifecycles lifecycles
           :task-scheduler :onyx.task-scheduler/balanced})
         :job-id
         (onyx.test-helper/feedback-exception! peer-config))

    (def red (take-segments! red-out-chan 50))

    (def blue (take-segments! blue-out-chan 50))

    (def green (take-segments! green-out-chan 50))

    (run-test-fixture :chrome
                      (fn [driver]
                        (Thread/sleep 25000)
                        (go driver (str "http://localhost:" 3000))
                        (load-last-deployment driver)
                        ;; (load-job driver)
                        ;; (check-job-text driver workflow)

                        (doseq [v-peer v-peers]
                          (onyx.api/shutdown-peer v-peer))

                        (onyx.api/shutdown-peer-group peer-group)

                        (onyx.api/shutdown-env env)))))
