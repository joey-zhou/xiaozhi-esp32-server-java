package com.xiaozhi.ai.probe;

import com.xiaozhi.ai.llm.factory.ChatModelFactory;
import com.xiaozhi.ai.stt.SttResult;
import com.xiaozhi.ai.stt.SttService;
import com.xiaozhi.ai.stt.SttServiceFactory;
import com.xiaozhi.common.model.bo.ConfigBO;
import com.xiaozhi.common.model.bo.ConfigProbeResultBO;
import com.xiaozhi.common.model.bo.RoleBO;
import com.xiaozhi.common.port.ProviderTokenClient;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * 配置试拨：拿一份已经定型的配置去 Provider 发起一次真实调用，把调用结果与报错翻译成给前端看的结论。
 * <p>只读，不落库，也不向下游共享缓存留下任何临时凭据；判断配置合不合规、拼哪份请求全部在这一层完成，
 * 调用方只负责决定「用哪份配置」。
 */
@Slf4j
@Component
public class ConfigProbe {

    /**
     * 测试用临时配置 ID 的取号器，取值落在负数区间，与真实配置的正数 ID 不重合。
     * <p>每次测试都要拿到独立的 ID。下游按 configId 键控 Token 与连接缓存，
     * 共用一个固定值会让不同用户的临时凭据落进同一个缓存槽。
     */
    private static final AtomicInteger TRANSIENT_CONFIG_ID_SEQ = new AtomicInteger();

    /** 送帧间隔，与样本音频每帧 60ms 的时长一致 */
    private static final Duration FRAME_INTERVAL = Duration.ofMillis(60);

    /** 允许试拨的接口协议：HTTP 族给模型接口，WebSocket 族给流式识别服务 */
    private static final Set<String> SUPPORTED_ENDPOINT_SCHEMES = Set.of("http", "https", "ws", "wss");

    /** 语音样本说的内容 */
    static final String SPEECH_TEXT = "你好";

    /** 配合图片样本的提问 */
    static final String IMAGE_QUESTION = "这张图片里有什么？请用一句话描述，说明形状和颜色。";

    /** 60ms 一帧，与设备上行 VAD 输出的分片粒度一致 */
    private static final int FRAME_BYTES = 1920;

    /** 首尾静音帧，供云端 ASR 判定语音端点 */
    private static final int LEADING_SILENCE_FRAMES = 2;
    private static final int TRAILING_SILENCE_FRAMES = 5;

    /**
     * 语音样本：本地 MeloTTS(vits-melo-tts-zh_en) 合成的中文「你好」，16kHz 单声道，
     * 按 G.711 μ-law 压到 8bit 后 base64 内嵌，取用时解回 16bit 小端 PCM。
     * STT 服务只接受 16kHz 单声道 16bit 小端 PCM，替换语料必须保持该格式。
     */
    private static final String SPEECH_MULAW_BASE64 =
            "/fH89fTk5Nzc3dvc3t7d3Nzd3d3d4N7h39/d3+Ls/PXn7vt+f21waGlla19oa2hfYV5eXF5fY191ZGltaGpqeWz9+np6fnV6"
            + "dfR6c3F8d251enz1eHf7fH5+6/nv7+73+Pbu7O3v7uzs8vftfvX4fm13enBsa2VlZl9eXl9eZmFrZ25kZ2RlbG1vcf70+u38"
            + "/PDz+XH6c3Z6+HNweW1vZnFmcW5sbnly9nb9bvJ5+XV+9f7y6erq6+Lk7O316ejj7eXu+Xf6bnJ68/P17Orn6Ojq6+jr7/t1"
            + "anvzc21rcGluZ2BjZWZlZmFgZV5nbmf4b+fo9+Dt3HDa39nq2/bq39xx4OjiauTy6W7y++v0ef7qfn5143nveuj9cv/sdnRu"
            + "9Hh6YXBpbV9ob2Nlcnxsavbv5uzm39XU2dfa2tva3N/l5+rjc/lsbV5ZVFVRTk1PTUtLTEpFR0lHRURFSUlLTE9TXWJ0/9zZ"
            + "z8vGw767ubq5t7W2tra3u7zCxcPI4eXPf1ZOT0M5ODU2LyssLismJyspJyovLzA3Rldd7MO4tbWtqamqp6SkpaSjpKeoqqmu"
            + "srS3wN192vBKOj8+NS0uKyciJSIgHR4fHhwcHiAfISctLjVM0cS8rqeiop+cm5ubmZmanJubnJ+foaWrr7O5xudcPzw7PC8r"
            + "LC4sJyQkIx4dHRwZGhsaGBgZGhocHyMoLTtX2sa0qqSinpyZmZiXlpeYl5eZm5ucn6Sprba+vtBVOzc0Mi4rLCsnJyQjHh4c"
            + "HBoYFxgWFRUWFxgaHSAnLT3+v7esopybmZeVlJKSkpSTlJSXmZucnqKorLO8vspbPDI0ST0qJTA2LCIlIx4ZHBwWERMXFQ8P"
            + "EhgXGR0mKi5Xt7Oxp5mYl5eUkZCRj4+RlpGRlZydm56qs7i22PpCPicpLkcqHyIuLCUdHh0cGBcUEhERExAPDxMWGh0gJThl"
            + "t6uloZqUk5GRj5COjY6Rk5KRlZqenp6lr7/Y2lleNTQmJjxLKSEuQzMrIR8dGxcTEQ8PERAPDQ8RExcbISs726+nopyXlZCO"
            + "jZCOjY2Pj5CRlJefn6Oir8nO22QzPzo4IyM2xygdJ888IBohIhcOEREPCg0REAsLExwZFx/0zt6ynZmbmI+Njo+MjIyNjY6P"
            + "kZSYmp6goqjFXdK8ZC4rODAeJr5CGh3MzR4VHCgYDgwPDgoKDQ4MCg4bIhoiVLOup5+WlZKPjI2Njo6MjI+Sk5KUl52hpKOl"
            + "w1PGuNk0PT02Iyy5Sh4c37coFRklHg4LDRALCQsODwoNGikfHjqto5+lm5OMj5KOio6TjoqPmJeQkpqlpZ+er/1KtME/KztH"
            + "Lx8lQWQbHyr4JhkWGRoPCQsLDQgKDA8NCxQgLCw4t56VnJ6Pio6UjYqMk5ONjJWamJGUnq+lnaXOTsO3zzE6RlolJTDKPB0g"
            + "P/skFxsZFQsJCgoGBgkLCwwPGyYyOeGsnJGTmJSMio2QkZGNjZCVlJOYmJaboqajq7/lw7i/PWNZyiMfN7gwHB0vNCMVFxMU"
            + "CwkHCwkGCQ4RDg8dM1I8sqKYl5GSjoyKj5GNi5GSj4+WlZeXm5ufqKyrrnlWrbJOM+p4JB4mQiYdGycqHBMPFBAIBggNCAcJ"
            + "DxISFR82ycOuoZeWl5ONjZGQi4uPkZCOj5CWl5WXnZ+en7zPrp62OEi72SsXKEEwEhkoKxcUGB4RCwcMDQwGCQ0UFRUXJzxY"
            + "Sa6gnJ6cko+RlJCNjpGQjo+SlZOVmaGenKC3yrigrrd31mRaLSImKxsbGyMbGBcYFg8PDAoJDAwLDRITGhsnMMHHsKacmZiX"
            + "joyOlI+LioyRkI2Ok5mYl5ult7KsrLm9v8DCZUcnHiEdFhIZHBUPFBsZEgwKDhEOCwwPGBoWHDDPRGOxnZqdnpWQjpSQj4yN"
            + "j5KPjY+VlJaZoaenoaCqrbC9zk00JBkUFhsVFxsdFhcbGxgREA4NDQwPDxITFRwpNjRFz6ujnJyVlJGTkI2Mjo6Qj4+OlJWV"
            + "maOtqp+ruKihrupD104eFxgbFBkZFhQVGRwaFxAODQ8PDw8PFhcdIC0+VDi9npemnpOOkZONio2Pjo2Oj46UlpmYpaumpKen"
            + "qq2ttf8zHRscFA4UHBQPFhsdGxkSDAwNDQ4PGRYTFixOOz5Er6mvppeQl5ePjI2PjY2QkZCQk5qdoKWroqSpqqioq7xGLx0Z"
            + "GRoQFRkWDRMfKR8eEw4NERMTEhsgGxc2rrA5T66epq6knJqenZePjY6RkpCOlpycmZ+xtaWZna+uo6LWKiQyJhQPFhkUER8k"
            + "GhIZFhMPERcUExQaHC9OMCUsQzU90aajpp6cmpSRkZOPj5CQkpOXm5+amJydnqGorrnDycoyHBIaIhgTHCYYDxIhKx4XEQ8P"
            + "DhEWHSIfHB82Pzpgu8U+U7WgnJuWlZWampeTk5WUlpaXmZucnJ+lqaWrrsDASDAmHRkbIR0VFhwgIh8rKSASDxAWGSElKigw"
            + "KiQmMO/JRUu3qa6kmI6Okpian6empJuXkpaXmZeTlJ+vtbl4MSs/NycdJSQpHxwbGRoaGBkjLh4WFiAnISMqLCQdHR43xbW5"
            + "YvDYtqmbk5ugrL+7o5eVkZOXoK2knJqbnKTJRk/G3NTrQywfIxwgKS4kHSMhHBIaHxwYGSopKDErJipGNyYpUrtURaydps+7"
            + "qaWjo56fn5udnJuYmJeYn6WloqzNzr+64joxLSsoJyQgHh8aGRwfIh8dHyokHyAjIx4hHighJzPSqKiqpqS5z7KoqaamqqSf"
            + "mJmYlpeeq6Sqp6qhpr7kubFyMzc6LyYjICcnIyMnLSgfGRofHh0lNi8tPzcsKS86OM3cube1uLSoqKespKq6uKyfm5eXn6zS"
            + "b8y5rKKhrMVENDhK0cY9KiInJy9KvEwnHyIdGyNNRi0oKSw8LzRI29LGMTrN0075w72xsLu8tKqltV5j0by6saShqcLJvre6"
            + "sqmvw7qvbUHBqqq8WjciGyEqL0TIx0MsIjIxMC06SDksLELrTjNFufcuLmLQSN6xvko6yb9aSLGwQi5ctru7raW7T8a+2tOv"
            + "r8LYubTMvr/NTTlD37CswOE7PC02XcG0w28+KCIvTjk4db1HICNAWzlIwU00NFVSOVrmOi5JtM1GzrjCQOS/wb+qqORc49TH"
            + "XMi1suo/OU+0qqOhpq67TjBBP0vT00D4xslIMk3MPEZGPjIxPDcuQcjcNzI/T0Q5Oz8/WuLN27zAyz9Ey7y9t8G+9eJtcuav"
            + "qKe3vsGxsLLIx7u800tESVLmVl5Sya/DLiIfHB8pSsW/bzolHy1fTy0+SDU4Srytrq/GMTBv/jM+ubdnRb61ysusus/Fsr/n"
            + "XLCssLu6ye3g2F8/QNW7z0xP0mM5NzYuP0ZaPjc5LiEn+9fLXFFBNS/rzritrrTJfungP3jOUr+1ucS1ubu7yK6ywtJD29K0"
            + "t8m9vrjQTEpPQ1nHePnOUy4pNDxlPTtEOS8yNzY+P1NBLUZ0X+a3yllG78pONtDH1MS4vcxlxdRHQ7WvyMXDtVpPyMFa8dNC"
            + "P+e8xkNEusfa/m9QOi4/QTI82d1IVN/HQkXeOB0fP0xAfq+r5ERhSi09u73bwK2v22TEbz5B07Czr66suMlEUuBDV8C42b6z"
            + "t1YyQ+p4aMG87EJAVEQxN0oyLNzJSTc/STorLdPPVUVg5zxsuK7AuLLPOSk9O1+7trp3Qj0/ReKuuOFDUEM8cLytury2zk1x"
            + "vb1QP13Jx7mur9Y/NS4pL+C73M/MTC8vTdRLxrDEbWbNUDNAxs9T1rW4W2vSPjxFtqyyrqq6Qz9GODRQva++wrzB6sv1UOXG"
            + "xb/LVD80MjhIT1I+PEM2LTlXdLq70/FmxtZdS0dBN1n6zs7Jubs7KSspLDbLpKKqrsotJyc2S8CopLJV3Ps1Nk3Dwse5rtg8"
            + "PkpBO0PIyk9QZFdAQ93Td1Ht+dRJOTxDSD9GTUZVRnVxU9S0u8XHw8xSZcTefldJQTpAxLK4vcPIbj9C38try7vA9NNXTjgt"
            + "PTg/X73F3Ug/XUJmvMNvZ9dMPTplzda+srxtzr6+v8y+ubzJzMjMvcvG0D85OUlIzbm5vnN+RTlJzsZ08+XbW0REOioqNmNy"
            + "z7nJSiwsLi40acW/vbK3yUxBVzxLvayyu8pZOz1XwcS/tb06NDIyPu23srznWTgpMDg7P1j0y8zOtsRXTD87O0Psvby5rLDG"
            + "z/RTVdjCucLH1kk3PEvNua2qp7bSWj81PU/Mta6vtLrMSjErKzE86768zc7bVzcuMTMrL0Lt8r+zv1U8PDcwPdXFw7q2w9PM"
            + "3lNMc97ixra0vcO9xlhO4c5k1cHUR0RMPzVGwslXTkcvLDROS0/d30csMjozPFvWPDM/Rj8/y8b0Yc/A3ci5utlf0fDmzLq0"
            + "v8vRzs3Jt7S3t7i82lNIeM7Jwr/UQDEvLS4yRVFUQ0A8ODAyNzs6PTs5Mzk8RE37zXNIQ0x3xKyhnZ2fn6iytbS3tqyjoKOl"
            + "p7PUTkA+QWbFvvRDNSYdGx8iLj3JzkUwJxwXFhofKjBKRTEsKy0sPO6wqKOdnZ+hn6KnoZ2amZmZnKKvx+tGT9C7s7S2yEgw"
            + "JyMhKCkkJCYmJSs7TEM5LCIaFxgYGx8nLCwuLSkoKj/ova2hnZyZl5SUkpGUmqClqq+tp6Ojo6Omp66xu3g9My8tLzg/STYs"
            + "KB8dHiQtOj43Kx8ZFhIREhUaISgvOzUvMThPv6yfnJudmZiXlZSXmp6jqauspqOkpaesvcfJ1mxEPTIoJSksLztYPi4qKCgq"
            + "N0xGLSQcFA0ODxMWHSo0Li4zLy86xqyfmpOSk5aXmp+ho5+joaKhp62urbGwr6+6xtbvWzk1Ly0qKy0zODIvLi4vPElVRC8h"
            + "GRIODg4RFhwfKSwzOUJS07OnnZqXlZOUlJeYmpudoaWtr7u8vre1rqyqrbO6zFg4LykjHiAnLjY+PURAPExFQTMrHhcRDg0O"
            + "DxUaHSQoLCsyOWa7qJ2YlJGPj5CRkpOVm56lrLKwq6ikoZ+hpqywv91qTz0wKiclIyYsO0M4OTs+O0Z6VTQjGhINCwwOEhge"
            + "IyYnKi862a+hm5eWlZWTlJSUlJWYnKSsvszVzLyvqaWmp621z2BBNS0pJyUlJSkvRllMSkxsV2JqRyweFg8MCgsOExkjLDIu"
            + "MjM5XLKhm5aTkpSXmZmZmZiZm6Gorq+3t7WxsrO3tri6vsXQRTosJx8eHh4gLEjYSz46OzY3STQtHBYOCwoMDhIYHyQpLC8/"
            + "TL6vop2Xk5COjo+Qk5aboaatsMO6u7m1rauus7S80E9DRj5FVfJMPzUsIyEoLC0tOFdcxrq4QCQYDwoICg4TGyQwLywxMDxe"
            + "rZ+Yk4+Oj5KUlpmZmZibnKGlr7e8u766srKytrO8vdfL6mxUTj8vKSYkIyMmIh8qPsvGuMo1HRENCgoMEhgeJzAtLS01PVa6"
            + "qJ2ZlJKTlZeanZ+enZ2cnJ2kq7XJa/rLvriyrq+2xXpBNTExLispJiMiIiQpKSAnNVRV4dI4IBUOCwsNFh4pOOpLPjc+T8Wp"
            + "nZaSj4+RlJWYmJqZmpqam52goaKkqauvucbI12hDPTUsJyIjIyMkJSIfHh0eHRkbIjhH07rNPCEXDw0OFR0lLkJHRDk9PknS"
            + "saWfmZSQjo2Oj5CRlJeYmJqfqbbfPTlEWc64ra24zEYwJSAeHR4fISAfHh0cHB4eHB8rSXa5sL0/IxgQDxEYHyk5Tkk4MTY/"
            + "3rCgmZSPj46Ojo6PkZWan6auvsnAvsHCvsrS429NOC8sJyEjJScoKiciHRoaGhsfHiAuv62rpKzMKBsRDw8VHCMrOD47NT5Z"
            + "47WnmpaQjo2Oj4+Rk5WWmJueo624ythe1sq/w8vnRS8oIyAfICQmKSopJB8eHR0dHiEeHitByryrsMM7JhwYGR0mLDhESTo6"
            + "OF/KrqKZlJGQkZOVl5qbnJ2fn6WqsK6vr7Kys7m9v7/GXjkqIx8eICMoKyokHxwbHR8cIDDewrKsuEwkGA8MDA8UGyQ1Oj06"
            + "SVa/qp2ZlpOTlZaVlJSUk5SWmp2mr7u9vLWuqaquutdCLiolIiQmJyYmJCAeHR0eHyMlJiMiIiMsN2TTzGlELyYeHh4jJzFC"
            + "bM3Iu7q0r6qjnpyamZiZmJiZm56iqa2vr62sq6moqa2vuMDZSDQqJSEeHiElHR4pQOW9q67nKhcOCgkKDBAXHyMjKC83PfG3"
            + "qp+cmJmampyfop+cmpmZmJqfpa20tqynoJ6eo7NxNCokJiotLzIvLSsqKSoqKSclIiIhJCcqLS4uLi80NTg7Ozg0NjZMzbWs"
            + "qq29bDguMULKr6WfnqGnrLCysK2ppaGfnp6en5+hoqaqsLrMYEM6NDYuKihByq6mo685HA0IBAUJDRMaHyEdHRseJDTotqeg"
            + "n6SprrSyq6SfnZydoamvtby4r6ijn6Gott9ANzhF5sK7vM5FMysqKzE9SEU+OTEvLjU7SU9LQjk1MTA1PlTv2MnLyuxqUF/d"
            + "vrStrKuvt85tTVrdwreuqKWlqKipq66vsrS0sbO1trm/z+hhWldMS0U9OjY+Q1BLQTEoHRgTEhEUFhkaHR4eISQrMUL3yb66"
            + "tbGuq6qnp6Slpqeoqaiop6enqaqtrrGxtbi9yelNPzs7Pj5BQUA9Ozk6Q0VYbudRPTUsJSEhIiInKy86QklIU2Ls28m8ta6r"
            + "qamqqqusra2sqqmpq62trq6vr6+yusfoTkc+Ozo6MTE+R2ToxNtfPS8mHxwbGhobHx8hJCksLzg/WtvAubSvra2srKinpKSi"
            + "pKaoqa6wtLa1s6+vr7K5ynBEOzg3Nzg7Ozc0MC8uMDY8Qk9OPzUrIx4dHh4hJiwxOD1AR1F02sa5r6yrrK2urq6rqKOhn56d"
            + "np6foqerra6wtb3OeU49NDEuLSspJyUlIyQmJysuLy8uKygnJCMjIyQmKCsvNDxIYsu9ta6trKysrKyrqaelo6SlqKqqqqmn"
            + "pqWmp6qttr7SdUU7NTMwLy4tLSsqKCYmJicnJyYlIyMjJCcrLzQ5P0dResu6sayopKKhoqSlp6iqqqqqqqusr7K3ury/wcfL"
            + "0d37U0Y8NzIuLi0uLS0sLCsrKioqKSgoKissLzQ4PUFGTVlv2cu/ubKvraqpp6WkpaeprK6zt7m5ubm6u8HJ0trk5n5dWFBK"
            + "RURAPDk1MC4rKyoqKy4wODs9PDo1NDU5PkZMX+Xc3d7ZzcW+vLm1s7OxsrGxsLO4ubq/x8vP1+N+7e5ub29mVktGQD9APj06"
            + "PDk5ODtCSVJZXlRNSkZGSldoaG9lWVFVWVxpcW1nb+jf2tbSz9DS0MrDwMHBxcTFxsnMzdfg+WxrXldYVFZcX19fYFpaUE5P"
            + "T1RVZvze2dDT3e7t/fLs39rp7W5dXQ==";

    /** 图片样本：64x64 白底红色实心圆的 PNG */
    private static final String IMAGE_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAArklEQVR42u3a0Q2EMAwE0e2/RnoBKjgM4fBmMxEFzJPyATba"
            + "Jz8CAAAAAAAAAPw4mzQT4MytPHaAYvc/JOpKf4uh9vpBgxzqRwxySB9hyKr+gWExwAf1dw0yrL9lkGd93bAGoKW+aADgD2is"
            + "rxgAAACQDWivvzRwhQAAAOAO4HUaAN/EEYDp50IJkzmGux6A6fcDCRuakB1ZwpYyYU8csqnP+Vfi+wMAAAAAANYGHLraNIF8"
            + "KFcaAAAAAElFTkSuQmCC";

    private static final byte[] SPEECH_PCM = decodeMulaw(Base64.getDecoder().decode(SPEECH_MULAW_BASE64));

    @Resource
    private ChatModelFactory chatModelFactory;

    @Resource
    private SttServiceFactory sttServiceFactory;

    @Resource
    private ProviderTokenClient tokenClient;

    /**
     * 试拨一份配置：配置类型只支持 llm/stt，其余类型直接拒绝。
     * <p>接口地址由调用方直接给，本进程能连到哪就能试到哪；私网地址是自建模型（Ollama、vLLM、MinIO 等）
     * 的正常用法，不能拦，因此只做协议白名单，并且不把下游报文回显给前端，避免退化成内网探测的读取通道。
     * <p>会改写入参的 configId（stt 测试用临时负数 ID），调用方若还要用这份 ConfigBO 做别的事，
     * 传进来之前先自行拷贝。
     */
    public ConfigProbeResultBO probe(ConfigBO config) {
        String configType = config.getConfigType();
        if (!"llm".equals(configType) && !"stt".equals(configType)) {
            return ConfigProbeResultBO.failure("暂不支持测试该类型配置");
        }
        String unsupportedEndpoint = unsupportedEndpointMessage(config.getApiUrl());
        if (unsupportedEndpoint != null) {
            return ConfigProbeResultBO.failure(unsupportedEndpoint);
        }
        try {
            if ("stt".equals(configType)) {
                return testStt(config);
            }
            if (ConfigBO.ModelType.embedding.getValue().equals(config.getModelType())) {
                var embeddingModel = chatModelFactory.getEmbeddingModel(config);
                float[] vector = embeddingModel.embed("测试");
                return ConfigProbeResultBO.success("连接成功，返回向量维度：" + vector.length);
            }
            if (ConfigBO.ModelType.vision.getValue().equals(config.getModelType())) {
                return testVision(config);
            }
            ChatModel chatModel = chatModelFactory.createChatModel(config, new RoleBO());
            ChatResponse response = chatModel.call(new Prompt(new UserMessage("你好，这是一次配置测试，请用一句话回复。")));
            String reply = extractReply(response);
            return ConfigProbeResultBO.success("连接成功，模型返回：" + reply);
        } catch (Exception e) {
            log.warn("配置测试失败 - provider: {}, model: {}", config.getProvider(), config.getConfigName(), e);
            return ConfigProbeResultBO.failure(extractErrorMessage(e));
        }
    }

    /**
     * 语音识别测试：把一段内嵌的 16k 单声道 PCM 当作一轮语音喂给识别服务，返回识别文本。
     */
    private ConfigProbeResultBO testStt(ConfigBO config) {
        // 表单里的临时凭据不能进 provider 缓存，configId 也不能带真实值去动线上连接与 Token 缓存
        config.setConfigId(nextTransientConfigId());
        SttResult result;
        try {
            SttService sttService = sttServiceFactory.createTransientSttService(config);
            // 按设备上行的节奏送帧，识别服务按实时流限速时才不会拒收
            result = sttService.stream(Flux.fromIterable(speechFrames()).delayElements(FRAME_INTERVAL));
        } finally {
            // 临时凭据换来的连接与 Token 用完即弃，不留在按 configId 键控的共享缓存里
            sttServiceFactory.removeCache(config);
            tokenClient.removeCache(config);
        }
        if (result == null) {
            return ConfigProbeResultBO.failure("识别服务未启动，请检查配置项是否填写完整");
        }
        if (result.operationFailed()) {
            return ConfigProbeResultBO.failure(sttFailureMessage(result.failureReason()));
        }
        if (!StringUtils.hasText(result.text())) {
            return ConfigProbeResultBO.failure("连接正常但没有识别出文本，请确认该模型支持 16kHz 中文语音");
        }
        return ConfigProbeResultBO.success("识别成功，测试语音说的是「" + SPEECH_TEXT + "」，识别结果：" + result.text());
    }

    private static int nextTransientConfigId() {
        return Integer.MIN_VALUE + (TRANSIENT_CONFIG_ID_SEQ.getAndIncrement() & Integer.MAX_VALUE);
    }

    private String sttFailureMessage(String failureReason) {
        return switch (failureReason) {
            case SttResult.FAILURE_UPSTREAM_ERROR -> "识别服务返回错误，请检查密钥、接口地址与网络";
            case SttResult.FAILURE_TIMEOUT -> "等待识别结果超时，请检查网络与服务可用性";
            default -> "本地识别处理异常，详情见服务端日志";
        };
    }

    /**
     * 视觉模型测试：带一张内嵌图片提问，验证图片通道而非只验证文本通道。
     */
    private ConfigProbeResultBO testVision(ConfigBO config) {
        ChatModel chatModel = chatModelFactory.createChatModel(config, new RoleBO());
        Media image = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(imagePng())
                .build();
        UserMessage message = UserMessage.builder()
                .media(image)
                .text(IMAGE_QUESTION)
                .build();
        ChatResponse response = chatModel.call(new Prompt(message));
        String reply = extractReply(response);
        return ConfigProbeResultBO.success("连接成功，测试图片是白底红色实心圆，模型返回：" + reply);
    }

    /**
     * 部分自研 ChatModel 在上游报错时会把异常吞掉、返回空 generations 的 ChatResponse
     * （避免打断正在进行的对话），试拨场景没有正在进行的对话可保护，这里直接转成明确的报错文案
     */
    private String extractReply(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new EmptyModelResponseException("模型未返回有效结果，请检查密钥、接口地址与模型名称是否正确");
        }
        return response.getResult().getOutput().getText();
    }

    /** 消息内容是自己拼的，不含下游细节，可以直接展示给前端 */
    private static class EmptyModelResponseException extends RuntimeException {
        EmptyModelResponseException(String message) {
            super(message);
        }
    }

    /**
     * 接口地址只放行 HTTP 与 WebSocket 两族协议：模型服务就这两种接法（FunASR 一类的识别服务走 ws），
     * 其余协议（file、gopher 等）拿不到模型响应，只会变成读本地资源的通道。
     * 地址没填时按 Provider 的内置默认端点走，不在这里判。
     *
     * @return 不合规时的提示文案，合规返回 null
     */
    private String unsupportedEndpointMessage(String apiUrl) {
        if (!StringUtils.hasText(apiUrl)) {
            return null;
        }
        URI uri;
        try {
            uri = URI.create(apiUrl.trim());
        } catch (IllegalArgumentException e) {
            return "接口地址格式不正确";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ENDPOINT_SCHEMES.contains(scheme)) {
            return "接口地址只支持 http/https/ws/wss";
        }
        // 主机名不在这里挑剔：URI 的解析比实际能连的地址严（如带下划线的容器名），拦下来会误伤正常配置
        return null;
    }

    /**
     * Provider 的原始报错体可能带下游内部地址、账号线索甚至密钥片段，只按状态码给一句能判读的结论，
     * 原文留在服务端日志里给运维看。
     */
    private String extractErrorMessage(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof RestClientResponseException rcre) {
                return upstreamFailureMessage(rcre.getStatusCode().value());
            }
            if (t instanceof EmptyModelResponseException) {
                return t.getMessage();
            }
            t = t.getCause();
        }
        return "连接测试失败，详情见服务端日志";
    }

    private String upstreamFailureMessage(int status) {
        if (status == 401 || status == 403) {
            return "接口返回未授权（" + status + "），请检查密钥与账号权限";
        }
        if (status == 404) {
            return "接口地址或模型名称不存在（404），请检查后重试";
        }
        if (status == 429) {
            return "接口返回限流（429），请稍后再试";
        }
        if (status >= 500) {
            return "接口返回服务端错误（" + status + "），请稍后再试或联系服务商";
        }
        return "接口返回错误（" + status + "），请检查接口地址、密钥与模型名称";
    }

    /**
     * 测试语音的 PCM 帧序列，首尾各补静音帧。
     */
    static List<byte[]> speechFrames() {
        List<byte[]> frames = new ArrayList<>();
        for (int i = 0; i < LEADING_SILENCE_FRAMES; i++) {
            frames.add(new byte[FRAME_BYTES]);
        }
        for (int offset = 0; offset < SPEECH_PCM.length; offset += FRAME_BYTES) {
            frames.add(Arrays.copyOfRange(SPEECH_PCM, offset, Math.min(offset + FRAME_BYTES, SPEECH_PCM.length)));
        }
        for (int i = 0; i < TRAILING_SILENCE_FRAMES; i++) {
            frames.add(new byte[FRAME_BYTES]);
        }
        return frames;
    }

    /**
     * 测试图片的 PNG 字节。
     */
    static byte[] imagePng() {
        return Base64.getDecoder().decode(IMAGE_PNG_BASE64);
    }

    /**
     * G.711 μ-law 解码为 16bit 小端 PCM。
     */
    private static byte[] decodeMulaw(byte[] mulaw) {
        byte[] pcm = new byte[mulaw.length * 2];
        for (int i = 0; i < mulaw.length; i++) {
            int coded = ~mulaw[i] & 0xFF;
            int magnitude = (((coded & 0x0F) << 3) + 0x84) << ((coded >> 4) & 0x07);
            int sample = (coded & 0x80) != 0 ? 0x84 - magnitude : magnitude - 0x84;
            pcm[i * 2] = (byte) sample;
            pcm[i * 2 + 1] = (byte) (sample >> 8);
        }
        return pcm;
    }
}
