package net.kdt.pojavlaunch.firefly.mobileglues;

/**
 * 渲染器查询：在一次性的 :mgquery 进程里执行。
 *
 * 每个方法都是阻塞的同步调用（runBench 可达一分多钟），binder 会占住调用方的一条线程，
 * 所以只能从后台线程发起。angleDirectory 传空串表示「不借 ANGLE」——AIDL 的 String
 * 不好表达 null，而渲染器那边空串与 null 本就同义（MG_ANGLE_DIR 的空值状态）。
 */
interface IMgQuery {
    /**
     * startSections = 起手场景规模，0 用渲染器默认值；maxSections = 本次不得越过的上限，
     * 0 表示没有。上一次跑分丢了 GL 上下文之后，调用方带着上限重来一次——崩过的那个规模
     * 只能由调用方记住，查询进程每次都是新的，它自己不可能知道。
     */
    String runBench(String mgDirectory, String angleDirectory, int startSections, int maxSections);
    int benchProgress();
    String glInfo(String mgDirectory, String angleDirectory);
}

