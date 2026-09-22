package com.cereal.client.application.exception

class MaxConcurrentTasksReachedException(
    maxNumberOfConcurrentTasks: Int,
) : CerealException("The maximum number of concurrent tasks is reached: $maxNumberOfConcurrentTasks")
