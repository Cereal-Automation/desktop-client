package com.cereal.client.infrastructure.provider.inmemory

import com.cereal.client.domain.provider.CheckoutProvider

/** No-op [CheckoutProvider] — completes immediately instead of opening a browser checkout. */
class InMemoryCheckoutProvider : CheckoutProvider {
    override suspend fun awaitCheckout(checkoutUrl: String) = Unit
}
