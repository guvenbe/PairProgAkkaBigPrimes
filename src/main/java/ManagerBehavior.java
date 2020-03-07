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
    public interface Command extends Serializable {
    }

    public static class InstructionCommand implements Command {
        public static final long serialVersionUID = 1L;
        private String message;
        private ActorRef<SortedSet<BigInteger>> sender;

        public InstructionCommand(String message, ActorRef<SortedSet<BigInteger>> sender) {
            this.message = message;
            this.sender = sender;
        }

        public String getMessage() {
            return message;
        }

        public ActorRef<SortedSet<BigInteger>> getSender() {
            return sender;
        }
    }

    public static class ResultCommand implements Command {
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
    private class NoResponseReceivedCommand implements Command {
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

    public static Behavior<Command> create() {
        return Behaviors.setup(ManagerBehavior::new);
    }

    private SortedSet<BigInteger> primes = new TreeSet<>();

    private ActorRef<SortedSet<BigInteger>> sender;

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(InstructionCommand.class, command -> {
                    this.sender=command.getSender();
                    if (command.getMessage().equals("start")) {
                        for (int workerCount = 0; workerCount < 20; workerCount++) {
                            ActorRef<WorkerBehavior.Command> worker = getContext().spawn(WorkerBehavior.create(), "worker" + workerCount);
                            askWorkerForAPrime(worker);
                        }
                    }
                    return Behaviors.same();
                })
                .onMessage(ResultCommand.class, command -> {
                    primes.add(command.getPrime());
                    System.out.println("I have received " + primes.size() + " primes numbers");
                    //Send the list of primes to the main method
                    if(primes.size() == 20){
                        this.sender.tell(primes);
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

    private void askWorkerForAPrime(ActorRef<WorkerBehavior.Command> worker) {
        getContext().ask(Command.class, worker, Duration.ofSeconds(5),
                //(me)->new WorkerBehavior.Command("start", getContext().getSelf()),
                (me) -> new WorkerBehavior.Command("start", me),
                (response, throwable) -> {
                    if (response != null) {
                        return response;
                    } else {
                        System.out.println("Worker " + worker.path() + " faile to respond");
                        return new NoResponseReceivedCommand(worker);
                    }
                });

    }
}
