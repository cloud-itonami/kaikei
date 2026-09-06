(ns kaikei.app-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [kaikei.app :as app]))

(use-fixtures :each
  {:before (fn [] (rf/clear-subscription-cache!) (reset! rf-db/app-db {}))})

(deftest initialize-db-sets-defaults
  (testing ":initialize-db populates the default status literal"
    (rf/dispatch-sync [:initialize-db])
    (is (= app/default-db @rf-db/app-db))
    (is (= "Kaikei Core Kaikei01" @(rf/subscribe [:title])))
    (is (= "etzhayyim-project-kaikei" @(rf/subscribe [:project])))
    (is (= "kaikei-core-kaikei01" @(rf/subscribe [:name])))
    (is (= "appview" @(rf/subscribe [:kind])))
    (is (= 0 @(rf/subscribe [:route-count])))
    (is (= [] @(rf/subscribe [:routes])))
    (is (= [] @(rf/subscribe [:vars])))
    (is (true? @(rf/subscribe [:xrpc?])))))

(deftest title-sub-reflects-db
  (testing ":title subscription reads whatever is in the db, not a fixed value"
    (reset! rf-db/app-db {:title "違うタイトル"})
    (is (= "違うタイトル" @(rf/subscribe [:title])))))

(deftest routes-sub-reflects-db
  (testing ":routes subscription reads whatever is in the db, not a fixed value"
    (reset! rf-db/app-db {:routes ["/xrpc/com.example.foo"]})
    (is (= ["/xrpc/com.example.foo"] @(rf/subscribe [:routes])))))

(deftest vars-sub-reflects-db
  (testing ":vars subscription reads whatever is in the db, not a fixed value"
    (reset! rf-db/app-db {:vars ["APP_NANOID"]})
    (is (= ["APP_NANOID"] @(rf/subscribe [:vars])))))

(deftest xrpc-sub-reflects-db
  (testing ":xrpc? subscription reads whatever is in the db, not a fixed value"
    (reset! rf-db/app-db {:xrpc? false})
    (is (false? @(rf/subscribe [:xrpc?])))))

(deftest initialize-db-overwrites-prior-state
  (testing ":initialize-db resets to defaults even if the db already had other data"
    (reset! rf-db/app-db {:title "stale" :unrelated 42})
    (rf/dispatch-sync [:initialize-db])
    (is (= app/default-db @rf-db/app-db))))
