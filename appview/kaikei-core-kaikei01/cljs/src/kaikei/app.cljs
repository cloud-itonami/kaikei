(ns kaikei.app
  "kaikei-core-kaikei01 appview — reagent + re-frame, view built from
  jp-go-dds (デジタル庁デザインシステム) hiccup.

  This is a faithful port of the previous Svelte 5 scaffold
  (`svelte/src/routes/+page.svelte`): a static status page showing the app's
  title/kind/name, the (empty) route list, the (empty) runtime-binding list,
  and the source path of the scaffold that generated it. It does not add
  accounting functionality (journal entries / trial balance / P&L / B/S) that
  was not already there — wrangler.jsonc's `APP_CAPABILITIES` names those, but
  this repo has never implemented them; see the repo README for what this
  surface actually is (an HTTP boundary, not the accounting engine)."
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [jp-go-dds.core :as dds]))

;; -- db ------------------------------------------------------------------
;;
;; The Svelte scaffold held this same shape as a plain `const app = {...}`
;; literal in the component script (static, not fetched). It is kept as
;; re-frame db + subs here so the migration actually exercises the
;; event/sub plumbing the workspace standard calls for, without inventing
;; app behaviour beyond what the original literal already described.

(def default-db
  {:title "Kaikei Core Kaikei01"
   :project "etzhayyim-project-kaikei"
   :name "kaikei-core-kaikei01"
   :kind "appview"
   :route-count 0
   :routes []
   :vars []
   :xrpc? true
   :relative-path "appview/kaikei-core-kaikei01/cljs/src/kaikei/app.cljs"})

(rf/reg-event-db
 :initialize-db
 (fn [_ _] default-db))

(rf/reg-sub :title (fn [db _] (:title db)))
(rf/reg-sub :project (fn [db _] (:project db)))
(rf/reg-sub :name (fn [db _] (:name db)))
(rf/reg-sub :kind (fn [db _] (:kind db)))
(rf/reg-sub :route-count (fn [db _] (:route-count db)))
(rf/reg-sub :routes (fn [db _] (:routes db)))
(rf/reg-sub :vars (fn [db _] (:vars db)))
(rf/reg-sub :xrpc? (fn [db _] (:xrpc? db)))
(rf/reg-sub :relative-path (fn [db _] (:relative-path db)))

;; -- view ------------------------------------------------------------------

(defn- list-or-empty [items empty-message]
  (if (seq items)
    (into [:ul {:class "dds-ext-stack"}]
          (map (fn [item] [:li item]) items))
    [:p {:class "dds-ext-lead"} empty-message]))

(defn app-view []
  (let [title @(rf/subscribe [:title])
        project @(rf/subscribe [:project])
        the-name @(rf/subscribe [:name])
        kind @(rf/subscribe [:kind])
        route-count @(rf/subscribe [:route-count])
        routes @(rf/subscribe [:routes])
        vars @(rf/subscribe [:vars])
        xrpc? @(rf/subscribe [:xrpc?])
        relative-path @(rf/subscribe [:relative-path])]
    (dds/container
     (dds/section
      {}
      [:p {:class "dds-ext-lead"} (str "Cloudflare " kind)]
      (dds/heading 1 title)
      [:span the-name])

     (dds/section
      {}
      (dds/grid
       {}
       (dds/card [:span "Project"] [:strong project])
       (dds/card [:span "Routes"] [:strong (str route-count)])
       (dds/card [:span "XRPC"] [:strong (if xrpc? "enabled" "not configured")])))

     (dds/section
      {:title "Public Routes"}
      (list-or-empty routes "No public route is declared next to this app surface."))

     (dds/section
      {:title "Runtime Bindings"}
      (list-or-empty vars "No public vars are declared in the nearest wrangler config."))

     (dds/section
      {:title "Source"}
      [:p relative-path]))))

;; -- init --------------------------------------------------------------------

(defn ^:export main []
  (rf/dispatch-sync [:initialize-db])
  (rdom/render [app-view] (js/document.getElementById "app")))
