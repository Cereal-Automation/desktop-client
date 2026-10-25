package com.cereal.client.application.exception

class TaskSetupHasRunningTasksException : CerealException("This task setup has running tasks. Stop them before editing it.")
