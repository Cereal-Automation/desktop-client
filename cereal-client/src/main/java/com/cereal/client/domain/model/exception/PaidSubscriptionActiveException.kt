package com.cereal.client.domain.model.exception

import com.cereal.client.application.exception.CerealException

/**
 * Raised when an operation cannot proceed because the script has an active paid subscription that
 * must be managed on the marketplace website (e.g. attempting to remove it locally).
 */
class PaidSubscriptionActiveException :
    CerealException(
        "This script has an active paid subscription. Please visit the marketplace website to manage your subscriptions.",
    )
