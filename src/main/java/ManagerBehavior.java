import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.Duration;
import java.util.SortedSet;
import java.util.TreeSet;

/*
* Using the ask pattern
*/

public class ManagerBehavior extends AbstractBehavior<ManagerBehavior.Command> {
    public interface Command extends Serializable{}

    public static class InstructionCommand implements  Command{
        public static final long serialVersionUID = 1L;
        private String message;

        public InstructionCommand(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class ResultCommand implements Command{
        public static final long serialVersionUID = 1L;
        private BigInteger prime;

        public ResultCommand(BigInteger prime) {
            this.prime = prime;
        }

        public BigInteger getPrime() {
            return prime;
        }
    }

    //This is called within the class
    private class NoResponseReceivedCommand implements Command{
        public static final long serialVersionUID = 1L;
        ActorRef<WorkerBehavior.Command> worker;

        public NoResponseReceivedCommand(ActorRef<WorkerBehavior.Command> worker) {
            this.worker = worker;
        }

        public ActorRef<WorkerBehavior.Command> getWorker() {
            return worker;
        }
    }

    private ManagerBehavior(ActorContext<Command> context) {
        super(context);
    }

    public static Behavior<Command> create(){
        return Behaviors.setup(ManagerBehavior::new);
    }

    private SortedSet<BigInteger> primes = new TreeSet<>();

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(InstructionCommand.class, command ->{
                    if(command.getMessage().equals("start")){
                        for(int workerCount=0; workerCount<20; workerCount++){
                            ActorRef<WorkerBehavior.Command> worker = getContext().spawn(WorkerBehavior.create(),"worker" +workerCount);
                            askWorkerForAPrime(worker);
                        }
                    }
                    return Behaviors.same();
                })
                .onMessage(ResultCommand.class, command ->{
                    primes.add(command.getPrime());
                    System.out.println("I have received " + primes.size() + " primes numbers");
                    if(primes.size()==20){
                        primes.forEach(System.out::println);
                    }
                    return Behaviors.same();
                })
                .onMessage(NoResponseReceivedCommand.class, command -> {
                    System.out.println("Retrying with worker " + command.worker.path());
                    askWorkerForAPrime(command.getWorker());
                    return Behaviors.same();
                })
                .build();
    }

    private void askWorkerForAPrime(ActorRef<WorkerBehavior.Command> worker){
        getContext().ask(Command.class, worker, Duration.ofSeconds(5),
                //(me)->new WorkerBehavior.Command("start", getContext().getSelf()),
                (me)->new WorkerBehavior.Command("start", me),
                (response, throwable) -> {
                    if(response != null){
                        return response;
                    } else {
                        System.out.println("Worker " + worker.path()+ " faile to respond");
                        return new NoResponseReceivedCommand(worker);
                    }
                });

    }
}
