package com.movtery.task

/**
 * 任务执行的各种阶段的监听器
 */
interface TaskExecutionPhaseListener {
    fun onBeforeStart() {}
    fun execute() {}
    fun onEnded() {}
    fun onFinally() {}
    /**
     * 任务执行中触发异常后将会执行的内容
     * @param throwable 触发的异常
     */
    fun onThrowable(throwable: Throwable) {}
}