/*
 * Copyright (c) 2021 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.test.android.uitests.tests.large.auth

import me.proton.core.account.domain.entity.AccountState.Ready
import me.proton.core.account.domain.entity.SessionState.Authenticated
import me.proton.core.test.android.instrumented.utils.StringUtils.randomString
import me.proton.core.test.android.plugins.data.Card
import me.proton.core.test.android.plugins.data.Plan
import me.proton.core.test.android.plugins.data.User
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.core.test.android.robots.auth.signup.RecoveryMethodsRobot
import me.proton.core.test.android.robots.humanverification.HumanVerificationRobot
import me.proton.core.test.android.robots.payments.AddCreditCardRobot
import me.proton.core.test.android.robots.plans.SelectPlanRobot
import me.proton.core.test.android.uitests.CoreexampleRobot
import me.proton.core.test.android.uitests.tests.BaseTest
import org.junit.Test

class SignupTests : BaseTest(defaultTimeout = 60_000L) {
    @Test
    fun signupFreeWithCaptchaAndRecoveryEmail() {
        val user = User(recoveryEmail = "${randomString()}@example.lt")
        AddAccountRobot()
            .createAccount()
            .setUsername(user.name)
            .setAndConfirmPassword<RecoveryMethodsRobot>(user.password)
            .email(user.recoveryEmail)
            .next<SelectPlanRobot>()
            .selectPlan<HumanVerificationRobot>(user.plan)
            .iAmHuman<CoreexampleRobot>()
            .verify { userStateIs(user, Ready, Authenticated) }

        CoreexampleRobot()
            .settingsRecoveryEmail()
            .verify {
                recoveryEmailElementsDisplayed()
                currentRecoveryEmailIs(user.recoveryEmail)
            }
    }

    @Test
    fun signupPlusWithCreditCard() {
        val user = User(plan = Plan.Dev)
        AddAccountRobot()
            .createAccount()
            .setUsername(user.name)
            .setAndConfirmPassword<RecoveryMethodsRobot>(user.password)
            .skip()
            .skipConfirm()
            .selectPlan<AddCreditCardRobot>(user.plan)
            .payWithCreditCard<CoreexampleRobot>(Card.default)
            .verify { userStateIs(user, Ready, Authenticated) }
    }
}
