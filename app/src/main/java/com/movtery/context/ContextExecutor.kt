package com.movtery.context

import android.app.Activity
import android.app.Application
import android.content.Context
import android.widget.Toast
import com.firefly.utils.ToastUtils.ShowToast
import com.movtery.task.TaskExecutors
import net.kdt.pojavlaunch.firefly.Tools
import net.kdt.pojavlaunch.firefly.lifecycle.ContextExecutorTask
import java.lang.ref.WeakReference

class ContextExecutor {
    companion object {
        private var sApplication: WeakReference<Application>? = null
        private var sActivity: WeakReference<Activity>? = null

        /**
         * Set the Application that will be used to execute tasks if the Activity won't be available.
         * @param application the application to use as the fallback
         */
        @JvmStatic
        fun setApplication(application: Application) {
            this.sApplication = WeakReference(application)
        }

        /**
         * Clear the Application previously set, so that ContextExecutor will notify the user of a critical error
         * that is executing code after the application is ended by the system.
         */
        @JvmStatic
        fun clearApplication() {
            this.sApplication?.clear()
        }

        /**
         * Set the Activity that this ContextExecutor will use for executing tasks
         * @param activity the activity to be used
         */
        @JvmStatic
        fun setActivity(activity: Activity) {
            this.sActivity = WeakReference(activity)
        }

        /**
         * Clear the Activity previously set, so the ContextExecutor won't use it to execute tasks.
         */
        @JvmStatic
        fun clearActivity() {
            this.sActivity?.clear()
        }

        /**
         * Schedules a ContextExecutorTask to be executed. For more info on tasks
         * @see ContextExecutorTask
         * @param task the task to be executed
         */
        @JvmStatic
        fun executeTask(task: ContextExecutorTask) {
            execute(
                activity = { activity ->
                    task.executeWithActivity(activity)
                },
                application = { application ->
                    task.executeWithApplication(application)
                }
            )
        }

        /**
         * 忽略Context是来自谁，直接使用这个Context执行任务
         * @see AllContextExecutorTask
         * @param task 想要执行的任务
         */
        @JvmStatic
        fun executeTaskWithAllContext(task: AllContextExecutorTask) {
            execute(
                activity = { task.execute(it) },
                application = { task.execute(it) }
            )
        }

        private fun execute(activity: (Activity) -> Unit, application: (Application) -> Unit) {
            TaskExecutors.runInUIThread {
                Tools.getWeakReference(this.sActivity)?.let {
                    activity(it)
                    return@runInUIThread
                }
                Tools.getWeakReference(this.sApplication)?.let {
                    application(it)
                    return@runInUIThread
                }
                throw RuntimeException("The Context has not been set!")
            }
        }

        /**
         * 通过这里保存的Activity获得res string
         * 如果Activity没有设置或者无法查找对应的res string，那么就会找到Application尝试获取
         * 如果仍旧失败，那么就只能接受报错了
         */
        @JvmStatic
        fun getString(resId: Int): String {
            return (this.sActivity?.get()?.getString(resId) ?: this.sApplication?.get()?.getString(resId))!!
        }

        /**
         * 在Java语言中，想要通过这个类来展示一个Toast会比较复杂
         * 这个函数就是用来解决这个痛点的XD
         * @param resId 要展示的文本的 res ID
         * @param duration 时长 LENGTH_SHORT LENGTH_LONG，与官方一致
         */
        @JvmStatic
        fun showToast(resId: Int, duration: Int) {
            executeTaskWithAllContext { context -> context.ShowToast(context.getString(resId), duration) }
        }

        /**
         * 在Java语言中，想要通过这个类来展示一个Toast会比较复杂
         * 这个函数就是用来解决这个痛点的XD
         * @param string 要展示的文本
         * @param duration 时长 LENGTH_SHORT LENGTH_LONG，与官方一致
         */
        @JvmStatic
        fun showToast(string: String, duration: Int) {
            executeTaskWithAllContext { context -> context.ShowToast(string, duration) }
        }

        /**
         * 尝试获取Activity
         * @throws RuntimeException 如果Activity不存在，那么将抛出异常
         */
        @JvmStatic
        fun getActivity(): Activity {
            return this.sActivity?.get() ?: throw RuntimeException("Activity does not exist.")
        }

        /**
         * 尝试获取Application
         * @throws RuntimeException 如果Application不存在，那么将抛出异常
         */
        @JvmStatic
        fun getApplication(): Application {
            return this.sApplication?.get() ?: throw RuntimeException("Application does not exist.")
        }
    }

    /**
     * A AllContextExecutorTask is a task that can dynamically change its behaviour, based on the context
     * used for its execution. This can be used to implement for ex. error/finish notifications from
     * background threads that may live with the Service after the activity that started them died.
     */
    fun interface AllContextExecutorTask {
        /**
         * 将会伴随着Activity或者是Application的Context执行的任务
         * @param context Activity或者是Application的Context
         */
        fun execute(context: Context)
    }
}