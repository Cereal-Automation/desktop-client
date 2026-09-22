package com.cereal.client.application.exception

class ScriptHasRunningTasksException : CerealException("This script has running tasks. Stop the running tasks before removing it.")
