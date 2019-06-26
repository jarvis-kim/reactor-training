package dev.flover.reactortraining;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import reactor.core.publisher.Flux;

@Slf4j
public class ErrorHandleringTests {


    /**
     * 특징
     * - exception 발생 이후 sequence 종료.
     * - subscribe에서 exception lambda로 throwable이 넘어오고 처리함.
     * - completeConsumer 호출 안됨.
     * - subscribe가 끝난 후에 main 스레드에서 계속 진행.
     */
    @Test
    public void test_handling_error_on_subscriber() {
        log.info("start test method");
        Flux.range(1, 10)
                .map(integer -> {
                    if (integer == 5) {
                        throw new RuntimeException("error on map");
                    }

                    return integer;
                })
                .subscribe(integer -> log.info("received value is {}.", integer),
                        throwable -> log.error("occurred error.", throwable),
                        () -> log.info("complete."));

        log.info("finish test method");
    }

    /**
     * 특징
     * - exception 발생 이후 sequence 종료.
     * - onErrorReturn으로 넘긴 값이 subscribe의 consumer로 넘어감.( -1 값)
     * - errorConsumer 호출 안됨.
     * - completeConsumer 호출 됨.(consumer에서 -1 값이 찍힌 후 호출)
     * - subscribe가 끝난 후에 main 스레드에서 계속 진행.
     */
    @Test
    public void test_handling_error_on_errorReturn() {
        log.info("start test method");
        Flux.range(1, 10)
                .map(integer -> {
                    if (integer == 5) {
                        throw new RuntimeException("error on map");
                    }

                    return integer;
                })
                .onErrorReturn(-1)
                .subscribe(integer -> log.info("received value is {}.", integer),
                        throwable -> log.error("occurred error.", throwable),
                        () -> log.info("complete."));

        log.info("finish test method");
    }

    /**
     * 특징
     * - exception 발생 이후 sequence 종료.
     * > 1, 2, 3 까지 발생했고, 3에서 IllegalArgumentException 발생 이후 sequence 발생 안함.
     * - onErrorResume을 통해 throwable을 받고 publisher를 리턴 한다. 아래에서는 30, 300을 생성한다.
     * - consumer에서 1, 2, 30, 300 을 받음.
     * - errorConsumer 호출 안됨.
     * - completeConsumer 호출 됨.(consumer에서 300 값이 찍힌 후 호출)
     * - subscribe가 끝난 후에 main 스레드에서 계속 진행.
     * <p>
     * 결론 적으로 onErrorResume을 사용하면 exception이 발생 이후 이전 stream을 끊기지만,
     * onErrorResume에서 받은 publisher를 생산을 다시 하는 것이다.
     * <p>
     * ## onErrorResume에서 Flux.error(throwable)를 리턴하면, subscribe의 consumer는 오출 안됨. 즉, 1, 2 호출 하고 끝.
     * ## 대신 subscribe의 errorConsumer 호출 됨.
     * ## completeConsumer 호출 안됨.
     * ## subscribe가 끝난 후에 main 스레드에서 계속 진행.
     */
    @Test
    public void test_handling_error_on_errorResume() {
        log.info("start test method");
        Flux.range(1, 10)
                .map(integer -> {
                    if (integer == 3) {
                        throw new IllegalArgumentException("error on map. value 3");
                    }

                    if (integer == 6) {
                        throw new IllegalStateException("error on map. value 6");
                    }

                    if (integer == 9) {
                        throw new RuntimeException("error on map. value 9");
                    }

                    return integer;
                })
                .onErrorResume(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        return Flux.just(30, 300);
                    } else if (throwable instanceof IllegalStateException) {
                        return Flux.just(60, 600);
                    }

                    return Flux.error(throwable);
                })
                .subscribe(integer -> log.info("received value is {}.", integer),
                        throwable -> log.error("occurred error.", throwable),
                        () -> log.info("complete."));
        log.info("finish test method");
    }

    /**
     * 특징
     * test_handling_error_on_subscriber 와 같다.
     * 다만, IllegalStateException이 onErrorMap을 통해 IllegalArgumentException로 변환 된다.
     */
    @Test
    public void test_handling_error_on_errorMap() {
        log.info("start test method");
        Flux.range(1, 10)
                .map(integer -> {
                    if (integer == 5) {
                        throw new IllegalStateException("error on map");
                    }

                    return integer;
                })
                .onErrorMap(IllegalArgumentException::new)
                .subscribe(integer -> log.info("received value is {}.", integer),
                        throwable -> log.error("occurred error.", throwable),
                        () -> log.info("complete."));

        log.info("finish test method");
    }

    /**
     * 특징
     * - exception이 발생하면 stream을 처음부터 발생 시킴.
     * - retry의 조건을 넘어서면(맞지 않으면) exception이 subscribe로 전달.
     * - errorConsumer 호출 됨.
     * - completeConsumer 호출 안됨.
     * - subscribe가 끝난 후에 main 스레드에서 계속 진행.
     * <p>
     * 출력 결과
     * received value is 1.
     * received value is 2.
     * received value is 3.
     * received value is 4.
     * received value is 1.
     * received value is 2.
     * received value is 3.
     * received value is 4.
     * occurred error.
     */
    @Test
    public void test_handling_error_on_retry() {
        log.info("start test method");
        Flux.range(1, 10)
                .map(integer -> {
                    if (integer == 5) {
                        throw new IllegalStateException("error on map");
                    }

                    return integer;
                })
                .retry(2)
                .subscribe(integer -> log.info("received value is {}.", integer),
                        throwable -> log.error("occurred error.", throwable),
                        () -> log.info("complete."));

        log.info("finish test method");
    }

    /**
     * 특징
     * - retry와 비슷하다.
     * - 하지만 subscribe의 errorConsumer는 호출 되지 않는다.
     * - subscribe의 completeConsumer가 호출된다.
     *
     * 위 동작 방식은 retryWhen 동작 방식에 따라 달라진다. 즉, retryWhen에서 어떤 데이터를 생성하냐에 따라 error를 subscribe로 전달 할지 결정된다.
     * retryWhene에서
     */
    @Test
    public void test_handling_error_on_retryWhen() {
        log.info("start test method");
        Flux.range(1, 10)
                .map(integer -> {
                    if (integer == 5) {
                        throw new IllegalStateException("error on map");
                    }

                    return integer;
                })
                .retryWhen(errorFlux -> errorFlux.take(2))  // or == Flux.just(2)
                .subscribe(integer -> log.info("received value is {}.", integer),
                        throwable -> log.error("occurred error.", throwable),
                        () -> log.info("complete."));

        log.info("finish test method");
    }

    /**
     * 특징
     * - test_handling_error_on_retryWhen 과 같지만,
     *   재시도 횟수가 3번째에는 exception을 발생 시킨다.
     *   그래서 원본 subscribe에서 errorConsumer가 호출 된다.
     */
    @Test
    public void test_handling_error_on_retryWhen_throw_error() {
        log.info("start test method");
        Flux.range(1, 10)
                .map(integer -> {
                    if (integer == 5) {
                        throw new IllegalStateException("error on map");
                    }

                    return integer;
                })
                .retryWhen(errorFlux ->
                        errorFlux.zipWith(
                                Flux.range(1, 3),
                                (throwable, index) -> {
                                    if (index < 3) {
                                        return index;
                                    }

                                    throw  new RuntimeException("runtime exception at retryWhen.");
                                })
                )
                .subscribe(integer -> log.info("received value is {}.", integer),
                        throwable -> log.error("occurred error.", throwable),
                        () -> log.info("complete."));

        log.info("finish test method");
    }

}
