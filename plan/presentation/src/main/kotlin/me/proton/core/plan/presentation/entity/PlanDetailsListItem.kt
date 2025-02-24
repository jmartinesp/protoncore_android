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

package me.proton.core.plan.presentation.entity

import android.os.Parcelable
import androidx.recyclerview.widget.DiffUtil
import kotlinx.android.parcel.Parcelize
import me.proton.core.plan.domain.entity.Plan
import me.proton.core.presentation.utils.PRICE_ZERO
import me.proton.core.presentation.utils.Price

sealed class PlanDetailsListItem(
    open val name: String,
    open val displayName: String,
    open val current: Boolean
) : Parcelable {

    @Parcelize
    data class FreePlanDetailsListItem(
        override val name: String,
        override val displayName: String,
        override val current: Boolean,
        val selectable: Boolean = true
    ) : PlanDetailsListItem(name, displayName, current)

    @Parcelize
    data class PaidPlanDetailsListItem(
        override val name: String,
        override val displayName: String,
        override val current: Boolean,
        val price: PlanPricing?,
        val selectable: Boolean = true,
        val upgrade: Boolean,
        val renewalDate: String?,
        val storage: Long,
        val members: Int,
        val addresses: Int,
        val calendars: Int,
        val domains: Int,
        val connections: Int,
        val currency: PlanCurrency
    ) : PlanDetailsListItem(name, displayName, current)

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<PlanDetailsListItem>() {
            override fun areItemsTheSame(oldItem: PlanDetailsListItem, newItem: PlanDetailsListItem) =
                oldItem.name == newItem.name

            override fun areContentsTheSame(oldItem: PlanDetailsListItem, newItem: PlanDetailsListItem) =
                oldItem == newItem
        }
    }
}

@Parcelize
data class PlanPricing(
    val monthly: Price,
    val yearly: Price,
    val twoYearly: Price? = null
) : Parcelable {

    companion object {
        fun fromPlan(plan: Plan) =
            plan.pricing?.let {
                PlanPricing(it.monthly.toDouble(), it.yearly.toDouble(), it.twoYearly?.toDouble())
            } ?: run {
                val cycle = PlanCycle.map[plan.cycle]
                val monthly = if (cycle == PlanCycle.MONTHLY) plan.amount else PRICE_ZERO
                val yearly = if (cycle == PlanCycle.YEARLY) plan.amount else PRICE_ZERO
                val twoYears = if (cycle == PlanCycle.TWO_YEARS) plan.amount else PRICE_ZERO
                PlanPricing(monthly.toDouble(), yearly.toDouble(), twoYears.toDouble())
            }
    }
}

