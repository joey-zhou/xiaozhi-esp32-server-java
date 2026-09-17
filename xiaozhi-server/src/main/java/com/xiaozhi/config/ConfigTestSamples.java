package com.xiaozhi.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 配置测试用的内嵌样本。
 *
 * <p>语音样本：本地 MeloTTS(vits-melo-tts-zh_en) 合成的中文「你好」，16kHz 单声道，
 * 按 G.711 μ-law 压到 8bit 后 base64 内嵌，取用时解回 16bit 小端 PCM。
 * STT 服务只接受 16kHz 单声道 16bit 小端 PCM，替换语料必须保持该格式。
 *
 * <p>图片样本：64x64 白底红色实心圆的 PNG。
 */
final class ConfigTestSamples {

    /** 语音样本说的内容 */
    static final String SPEECH_TEXT = "你好";

    /** 配合图片样本的提问 */
    static final String IMAGE_QUESTION = "这张图片里有什么？请用一句话描述，说明形状和颜色。";

    /** 60ms 一帧，与设备上行 VAD 输出的分片粒度一致 */
    private static final int FRAME_BYTES = 1920;

    /** 首尾静音帧，供云端 ASR 判定语音端点 */
    private static final int LEADING_SILENCE_FRAMES = 2;
    private static final int TRAILING_SILENCE_FRAMES = 5;

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

    private static final String IMAGE_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAArklEQVR42u3a0Q2EMAwE0e2/RnoBKjgM4fBmMxEFzJPyATba"
            + "Jz8CAAAAAAAAAPw4mzQT4MytPHaAYvc/JOpKf4uh9vpBgxzqRwxySB9hyKr+gWExwAf1dw0yrL9lkGd93bAGoKW+aADgD2is"
            + "rxgAAACQDWivvzRwhQAAAOAO4HUaAN/EEYDp50IJkzmGux6A6fcDCRuakB1ZwpYyYU8csqnP+Vfi+wMAAAAAANYGHLraNIF8"
            + "KFcaAAAAAElFTkSuQmCC";

    private static final byte[] SPEECH_PCM = decodeMulaw(Base64.getDecoder().decode(SPEECH_MULAW_BASE64));

    private ConfigTestSamples() {
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
