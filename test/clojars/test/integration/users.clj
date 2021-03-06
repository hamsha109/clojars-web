(ns clojars.test.integration.users
  (:require [clojure.test :refer :all]
            [kerodon.core :refer :all]
            [kerodon.test :refer :all]
            [clojars.test.integration.steps :refer :all]
            [clojars.web :as web]
            [clojars.test.test-helper :as help]
            [net.cgrand.enlive-html :as enlive]))

(use-fixtures :each
  help/default-fixture
  help/with-clean-database)

(deftest user-can-register
  (-> (session (help/app))
      (register-as "dantheman" "test@example.org" "password")
      (follow-redirect)
      (has (status? 200))
      (within [:div.light-article :> :h1]
              (has (text? "Dashboard (dantheman)")))))

(deftest bad-registration-info-should-show-error
  (-> (session (help/app))
      (register-as "fixture" "fixture@example.org" "password"))
  (-> (session (help/app))
      (visit "/")
      (follow "register")
      (has (status? 200))
      (within [:title]
              (has (text? "Register - Clojars")))

      (fill-in "Email" "test@example.org")
      (fill-in "Username" "dantheman")
      (press "Register")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Password can't be blankPassword must be 8 characters or longer")))

      (fill-in "Password" "password")
      (fill-in "Email" "test@example.com")
      (fill-in "Username" "dantheman")
      (press "Register")
      (has (status? 200))
      (has (value? [:input#username] "dantheman"))
      (has (value? [:input#email] "test@example.com"))
      (within [:div.error :ul :li]
              (has (text? "Password and confirm password must match")))

      (fill-in "Email" "")
      (fill-in "Username" "dantheman")
      (fill-in "Password" "password")
      (fill-in "Confirm password" "password")
      (press "Register")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Email can't be blankEmail must have an @ sign and a domain")))

      (fill-in "Email" "test@example.org")
      (fill-in "Username" "")
      (fill-in "Password" "password")
      (fill-in "Confirm password" "password")
      (press "Register")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Username must consist only of lowercase letters, numbers, hyphens and underscores.Username can't be blank")))
      (fill-in "Username" "<script>")
      (fill-in "Password" "password")
      (fill-in "Confirm password" "password")
      (press "Register")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Username must consist only of lowercase letters, numbers, hyphens and underscores.")))

      (fill-in "Username" "fixture")
      (fill-in "Password" "password")
      (fill-in "Confirm password" "password")
      (press "Register")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Username is already taken")))))

(deftest user-can-update-info
  (-> (session (help/app))
      (register-as "fixture" "fixture@example.org" "password")
      (follow-redirect)
      (follow "profile")
      (fill-in "Email" "fixture2@example.org")
      (fill-in "Current password" "password")
      (fill-in "New password" "password2")
      (fill-in "Confirm new password" "password2")
      (press "Update")
      (follow-redirect)
      (within [:div#notice]
              (has (text? "Profile updated.")))
      (follow "logout")
      (follow-redirect)
      (has (status? 200))
      (within [:nav [:li enlive/first-child] :a]
              (has (text? "login")))
      (login-as "fixture" "password2")
      (follow-redirect)
      (has (status? 200))
      (within [:div.light-article :> :h1]
              (has (text? "Dashboard (fixture)")))))

(deftest bad-update-info-should-show-error
  (-> (session (help/app))
      (register-as "fixture" "fixture@example.org" "password")
      (follow-redirect)
      (follow "profile")
      (has (status? 200))
      (within [:title]
              (has (text? "Profile - Clojars")))

      (fill-in "Current password" "")
      (press "Update")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Current password can't be blankCurrent password is incorrect")))

      (fill-in "Current password" "wrong-password")
      (press "Update")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Current password is incorrect")))

      (fill-in "New password" "newpassword")
      (fill-in "Confirm new password" "newpassword")
      (press "Update")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Current password can't be blankCurrent password is incorrect")))

      (fill-in "Current password" "password")
      (fill-in "New password" "newpassword")
      (press "Update")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Password and confirm password must match")))

      (fill-in "Current password" "password")
      (fill-in "Email" "")
      (press "Update")
      (has (status? 200))
      (within [:div.error :ul :li]
              (has (text? "Email can't be blankEmail must have an @ sign and a domain")))))

(deftest user-can-get-new-password
  (let [transport (promise)]
    (-> (session (help/app))
        (register-as "fixture" "fixture@example.org" "password"))
    (-> (session (help/app {:mailer (fn [& x] (deliver transport x))}))
        (visit "/")
        (follow "login")
        (follow "Forgot your username or password?")
        (fill-in "Email or Username" "fixture")
        (press "Email me a password reset link")
        (has (status? 200))
        (within [:p]
                (has (text? "If your account was found, you should get an email with a link to reset your password soon."))))
    (let [[to subject message :as email] (deref transport 100 nil)]
      (is email)
      (is (= to "fixture@example.org"))
      (is (= subject "Password reset for Clojars"))
      (let [password "some-secret!"
            [_ reset-password-link]
            (re-find
             #"Hello,\n\nWe received a request from someone, hopefully you, to reset the password of the clojars user: fixture.\n\nTo contine with the reset password process, click on the following link:\n\n([^ ]+)\n\n"
             message)]
        (is (string? reset-password-link))
        (-> (session (help/app))
            (visit reset-password-link)
            (has (status? 200))
            (fill-in "New password" password)
            (fill-in "Confirm new password" password)
            (press "Update my password")
            (follow-redirect)
            (has (status? 200))
            (within [:div.small-section :> :h1]
                    (has (text? "Login")))

                                        ; can login with new password
            (login-as "fixture" password)
            (follow-redirect)
            (has (status? 200))
            (within [:div.light-article :> :h1]
                    (has (text? "Dashboard (fixture)"))))))))

(deftest bad-reset-code-shows-message
  (-> (session (help/app))
      (visit "/password-resets/this-code-does-not-exist")
      (has (status? 200))
      (within [:p]
        (has (text? "The reset code was not found. Please ask for a new code in the forgot password page")))))

(deftest admin-can-add-user-to-group
  (-> (session (help/app))
      (register-as "fixture" "fixture@example.org" "password"))
  (-> (session (help/app))
      (register-as "dantheman" "test@example.org" "password")
      (visit "/groups/org.clojars.dantheman")
      (fill-in [:#username] "fixture")
      (press "Add Member")
      ;;(follow-redirect)
      (within [:div.small-section
               :table
               [:tr enlive/last-of-type]
               [:td enlive/first-of-type]]
              (has (text? "fixture")))))

(deftest user-must-exist-to-be-added-to-group
  (-> (session (help/app))
      (register-as "dantheman" "test@example.org" "password")
      (visit "/groups/org.clojars.dantheman")
      (fill-in [:#username] "fixture")
      (press "Add Member")
      (within [:div.error :ul :li]
              (has (text? "No such user: fixture")))))

(deftest users-can-be-viewed
  (-> (session (help/app))
      (register-as "dantheman" "test@example.org" "password")
      (visit "/users/dantheman")
      (within [:div.light-article :> :h1]
              (has (text? "dantheman")))))
