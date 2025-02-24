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

package me.proton.core.plan.presentation.viewmodel

import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.domain.entity.UserId
import me.proton.core.payment.domain.usecase.GetCurrentSubscription
import me.proton.core.payment.presentation.PaymentsOrchestrator
import me.proton.core.payment.presentation.entity.BillingResult
import me.proton.core.payment.presentation.entity.PlanShortDetails
import me.proton.core.payment.presentation.onPaymentResult
import me.proton.core.plan.domain.SupportedPaidPlans
import me.proton.core.plan.domain.entity.Plan
import me.proton.core.plan.domain.usecase.GetPlans
import me.proton.core.plan.presentation.entity.PlanCurrency
import me.proton.core.plan.presentation.entity.PlanDetailsListItem
import me.proton.core.plan.presentation.entity.PlanPricing
import me.proton.core.plan.presentation.entity.PlanSubscription
import me.proton.core.plan.presentation.entity.PlanType
import me.proton.core.plan.presentation.entity.SelectedPlan
import me.proton.core.plan.presentation.entity.SelectedPlan.Companion.FREE_PLAN_ID
import me.proton.core.presentation.viewmodel.ProtonViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val getPlans: GetPlans,
    private val getCurrentSubscription: GetCurrentSubscription,
    @SupportedPaidPlans val supportedPaidPlans: List<String>,
    private val paymentsOrchestrator: PaymentsOrchestrator
) : ProtonViewModel() {

    private val _availablePlansState = MutableStateFlow<State>(State.Idle)

    val availablePlansState = _availablePlansState.asStateFlow()

    sealed class State {
        object Idle : State()
        object Processing : State()
        sealed class Success : State() {
            data class Plans(
                val plans: List<PlanDetailsListItem>,
                val subscription: PlanSubscription? = null
            ) : Success()

            data class PaidPlanPayment(val selectedPlan: SelectedPlan, val billing: BillingResult) : Success()
        }

        sealed class Error : State() {
            data class Message(val message: String?) : Error()
        }
    }

    fun getCurrentPlanWithUpgradeOption(userId: UserId? = null, showFreeIfCurrent: Boolean = true) = flow {
        emit(State.Processing)
        val upgrade: Boolean = userId != null
        val currentSubscription = if (userId != null) {
            getCurrentSubscription(userId)
        } else null

        val subscribedPlans: MutableList<PlanDetailsListItem> = currentSubscription?.plans?.filter {
            it.type == PlanType.NORMAL.value
        }?.map {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = currentSubscription.periodEnd * 1000
            PlanDetailsListItem.PaidPlanDetailsListItem(
                name = it.name,
                displayName = it.title,
                price = PlanPricing.fromPlan(it),
                selectable = false,
                current = true,
                upgrade = upgrade,
                renewalDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(calendar.time),
                storage = it.maxSpace,
                members = it.maxMembers,
                addresses = it.maxAddresses,
                calendars = it.maxCalendars,
                domains = it.maxDomains,
                connections = it.maxVPN,
                currency = PlanCurrency.valueOf(it.currency)
            )
        }?.toMutableList() ?: mutableListOf()

        val plans: MutableList<PlanDetailsListItem> = if (subscribedPlans.isEmpty() && showFreeIfCurrent) {
            // this means that users current plan is free
            mutableListOf(createFreePlanAsCurrent(current = upgrade, selectable = !upgrade))
        } else subscribedPlans

        if (subscribedPlans.isEmpty()) {
            plans.addAll(getPlans(supportedPaidPlans = supportedPaidPlans + subscribedPlans.map {
                it.name
            }, userId = userId)
                .map { it.toPaidPlanDetailsItem(subscribedPlans, upgrade) }
            )
        }

        val planSubscription = PlanSubscription(currentSubscription,
            subscribedPlans.isEmpty() || subscribedPlans.find {
                supportedPaidPlans.contains(it.name)
            } != null)
        emit(State.Success.Plans(plans = plans, subscription = planSubscription))
    }.catch { error ->
        _availablePlansState.tryEmit(State.Error.Message(error.message))
    }.onEach { plans ->
        _availablePlansState.tryEmit(plans)
    }.launchIn(viewModelScope)

    fun register(context: Fragment) {
        paymentsOrchestrator.register(context)
    }

    fun startBillingForPaidPlan(userId: UserId?, selectedPlan: SelectedPlan) {
        with(paymentsOrchestrator) {
            onPaymentResult { result ->
                result.let { billingResult ->
                    if (billingResult?.paySuccess == true) {
                        viewModelScope.launch {
                            _availablePlansState.emit(State.Success.PaidPlanPayment(selectedPlan, billingResult))
                        }
                    }
                }
            }

            startBillingWorkFlow(
                userId = userId,
                selectedPlan = PlanShortDetails(
                    name = selectedPlan.planName,
                    displayName = selectedPlan.planDisplayName,
                    subscriptionCycle = selectedPlan.cycle.toSubscriptionCycle(),
                    currency = selectedPlan.currency.toSubscriptionCurrency()
                ),
                codes = null
            )
        }
    }

    private fun createFreePlanAsCurrent(current: Boolean, selectable: Boolean): PlanDetailsListItem {
        return PlanDetailsListItem.FreePlanDetailsListItem(
            name = FREE_PLAN_ID,
            displayName = FREE_PLAN_ID,
            current = current,
            selectable = selectable
        )
    }
}

fun Plan.toPaidPlanDetailsItem(subscribedPlans: MutableList<PlanDetailsListItem>, upgrade: Boolean) =
    PlanDetailsListItem.PaidPlanDetailsListItem(
        name = name,
        displayName = title,
        price = PlanPricing.fromPlan(this),
        selectable = if (upgrade) true else subscribedPlans.isNullOrEmpty(),
        current = subscribedPlans.find { currentPlan ->
            currentPlan.name == id
        } != null,
        upgrade = upgrade,
        renewalDate = null,
        storage = maxSpace,
        members = maxMembers,
        addresses = maxAddresses,
        calendars = maxCalendars,
        domains = maxDomains,
        connections = maxVPN,
        currency = PlanCurrency.valueOf(currency)
    )
