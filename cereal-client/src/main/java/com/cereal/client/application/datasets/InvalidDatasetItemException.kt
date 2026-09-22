package com.cereal.client.application.datasets

import com.cereal.client.application.exception.CerealException

class InvalidDatasetItemException(
    invalidFields: Map<String, String?>,
) : CerealException("${invalidFields.keys.joinToString(", ")} contains an invalid value.")
