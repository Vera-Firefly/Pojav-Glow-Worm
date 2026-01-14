package com.movtery.task

import androidx.annotation.CheckResult
import java.util.concurrent.Callable
import java.util.concurrent.Executor

abstract class Task<V>: TaskExecutionPhaseListener {
    private var executor: Executor = TaskExecutors.getDefault()
    private var throwableFromTask: Throwable? = null
    private var beforeStart: Pair<Runnable, Executor>? = null
    private var ended: Pair<OnTaskEndedListener<V>, Executor>? = null
    private var finally: Pair<Runnable, Executor>? = null
    private var onTaskThrowable: Pair<OnTaskThrowableListener, Executor>? = null
    private var result: V? = null

    protected abstract fun performMainTask()

    @CheckResult(SUGGEST)
    open fun setExecutor(executor: Executor): Task<V> {
        this.executor = executor
        return this
    }

    private fun setThrowable(e: Throwable) {
        this.throwableFromTask = e
    }

    private fun checkThrowable() {
        throwableFromTask?.let {
            onThrowable(it)
        }
    }

    /**
     * 与主任务使用同一个执行者
     */
    @CheckResult(SUGGEST)
    fun beforeStart(runnable: Runnable): Task<V> {
        return beforeStart(this.executor, runnable)
    }

    @CheckResult(SUGGEST)
    fun beforeStart(executor: Executor, runnable: Runnable): Task<V> {
        this.beforeStart = Pair(runnable, executor)
        return this
    }

    /**
     * 与主任务使用同一个执行者
     */
    @CheckResult(SUGGEST)
    fun ended(listener: OnTaskEndedListener<V>): Task<V> {
        return ended(this.executor, listener)
    }

    @CheckResult(SUGGEST)
    fun ended(executor: Executor, listener: OnTaskEndedListener<V>): Task<V> {
        this.ended = Pair(listener, executor)
        return this
    }

    /**
     * 与主任务使用同一个执行者
     */
    @CheckResult(SUGGEST)
    fun finallyTask(runnable: Runnable): Task<V> {
        return finallyTask(this.executor, runnable)
    }

    @CheckResult(SUGGEST)
    fun finallyTask(executor: Executor, runnable: Runnable): Task<V> {
        this.finally = Pair(runnable, executor)
        return this
    }

    /**
     * 与主任务使用同一个执行者
     */
    @CheckResult(SUGGEST)
    fun onThrowable(listener: OnTaskThrowableListener): Task<V> {
        return onThrowable(this.executor, listener)
    }

    @CheckResult(SUGGEST)
    fun onThrowable(executor: Executor, listener: OnTaskThrowableListener): Task<V> {
        this.onTaskThrowable = Pair(listener, executor)
        return this
    }

    fun setResult(result: V) {
        this.result = result
    }

    override fun onBeforeStart() {
        this.beforeStart?.let { r ->
            r.second.execute {
                runCatching { r.first.run() }.getOrElse { t -> setThrowable(t) }
            }
        }
    }

    override fun execute() {
        onBeforeStart()
        checkThrowable()

        this.executor.execute run@{
            runCatching {
                performMainTask()
                onEnded()
            }.getOrElse { t -> setThrowable(t) }
            checkThrowable()
            onFinally()
        }
    }

    override fun onEnded() {
        this.ended?.let { r ->
            r.second.execute {
                runCatching { r.first.onEnded(result) }.getOrElse { t -> onThrowable(t) }
            }
        }
    }

    override fun onFinally() {
        this.finally?.let { r ->
            r.second.execute {
                runCatching { r.first.run() }
            }
        }
    }

    override fun onThrowable(throwable: Throwable) {
        this.onTaskThrowable?.let { r ->
            r.second.execute {
                runCatching { r.first.onThrowable(throwable) }
            }
        }
    }

    companion object {
        const val SUGGEST = "NOT_REQUIRED_TO_EXECUTE"

        @CheckResult(SUGGEST)
        @JvmStatic
        fun <V> runTask(callable: Callable<V>): Task<V> {
            return SimpleTask(callable)
        }

        @CheckResult(SUGGEST)
        @JvmStatic
        fun <V> runTask(executor: Executor, callable: Callable<V>): Task<V> {
            return SimpleTask(callable).setExecutor(executor)
        }
    }
}