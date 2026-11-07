package com.cereal.client.domain.model.task

/** A confirmation's words outside its window: the heads-up and remote approval. The window's own are in strings.xml. */
object ConfirmationText {
    const val WAITING_IN_CEREAL = "Waiting for your answer in Cereal."
    const val EXPIRES_AT = "Expires at %s"
    const val CONFIRMED = "Confirmed"
    const val DECLINED = "Declined"
    const val EXPIRED = "Expired"
    const val NO_LONGER_WAITING = "No longer waiting"
    const val STALE_PRESS = "This confirmation is no longer waiting"
}
