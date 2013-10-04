(ns lobos.config
  (:use lobos.connectivity)
  (:require [metrocene.models.db :as schema]))

(open-global schema/db)