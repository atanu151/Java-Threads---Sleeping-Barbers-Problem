import java.util.Scanner;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Timer;
import java.util.TimerTask;
 
public class SleepingBarbersProblem {
	static int interval;
	static Timer timer;
 
    public static void main(String args[])
    {
    	System.out.println("***Opening up the Barber-Shop***") ;
    	
    	
    	// Taking input from user to define the time of functioning of the Barber-shop
    	Scanner input1 = new Scanner(System.in);
    	int time = -1;
    	do {
    		System.out.print("How long do you want the Barber-Shop to be open (in seconds)? : ");
    	    String next = input1.next();
    	    try {
    	    	time = Integer.parseInt(next);
    	    } catch (NumberFormatException exp) {
    	    }
    	} while (time < 1);
    	
    	
    	// Taking input from user to define the number of barbers present in the shop
    	Scanner input2 = new Scanner(System.in);
    	int number = -1;
    	do {
    		System.out.print("Please enter the number of barbers available in the shop (> '0') :") ;
    	    String next = input2.next();
    	    try {
    	    	number = Integer.parseInt(next);
    	    } catch (NumberFormatException exp) {
    	    }
    	} while (number < 1);
    	
    	
    	//Generating the Barber threads to run on Multi-Cores
    	final int numberofbarbers = number ;
    	Barbershop salon = new Barbershop(numberofbarbers);
        ExecutorService openUp = Executors.newFixedThreadPool(numberofbarbers);
        Barber[]  worker = new Barber[numberofbarbers];
        for(int i = 0; i < numberofbarbers; i++) {
            worker[i]= new Barber(salon);
            Barber barber = worker[i] ;
            barber.setName(barber.getID());
            openUp.execute(barber);
        }
        
        
        //Creating a thread which would generate customer threads in random intervals
        CustomerCreator creator = new CustomerCreator(salon);
        Thread threadcreator = new Thread(creator);
        threadcreator.start();
        
        
        //Defining the timer and managing all the actors so that the Barber-shop closes down properly on the time defined
        int delay = 1000;
	    int period = 1000;
	    timer = new Timer();
	    interval = time;
	    timer.scheduleAtFixedRate(new TimerTask() {
	        public void run() {
	            if (checktimer() < 1) 
	            {
	            	//stopping new customer threads from being generated 
	    	    	creator.stopRunning() ;
	    	    	System.out.println("Barber-shop is closing and no new customer will be served apart from the ones who are already having a haircut.");
	    	    	//shutting down executor service
	    	    	openUp.shutdown();
	    	    	//stopping the barber threads as they function under a "while" condition and will keep on running if not stopped
	    	    	for(int i = 0; i < numberofbarbers; i++) {
	    	    		worker[i].stopRunning();
	    	    	}
	    	    	
	    	    	salon.leave() ;
	    	    	
	    	    	try {
	    	    	     // Wait for existing tasks to complete
	    	    	     if (!openUp.awaitTermination(20, TimeUnit.SECONDS)) {
	    	    	       openUp.shutdownNow(); 
	    	    	       // Wait for tasks to respond to being cancelled
	    	    	       if (!openUp.awaitTermination(20, TimeUnit.SECONDS))
	    	    	           System.err.println("The thread Pool did not terminate properly.");
	    	    	     }
	    	    	   } catch (InterruptedException ie) {
	    	    	     openUp.shutdownNow();
	    	    	   }
	    	    	
	    	    	while (!openUp.isTerminated()) {
	                    // wait for all tasks to finish execution
	                }
	    	    	System.out.println("***Barber-Shop is closed now***");
	    	    	input1.close();
	    	    	input2.close();
	    	    } 
	        }
	    }, delay, period);   
    }
    
    private static final int checktimer() {
	    if (interval == 1)
	        timer.cancel();
	    return --interval;
	}
}

class Barbershop
{
    int waitingchairs;
    List<Customer> customerqueue;
    int freebarber;
    
    public Barbershop(int numberofbarbers)
    {
    	freebarber = numberofbarbers ;
    	
    	//Taking input from user to define the number of waiting chairs
    	Scanner input3 = new Scanner(System.in);
    	int numberofchairs = -1;
    	do {
    		System.out.print("Please enter the number of waiting chairs in the shop (> '0') :") ;
    	    String next = input3.next();
    	    try {
    	    	numberofchairs = Integer.parseInt(next);
    	    } catch (NumberFormatException exp) {
    	    }
    	} while (numberofchairs < 1);
    	
    	waitingchairs = numberofchairs;
        customerqueue = new LinkedList<Customer>();
        input3.close(); 
    }
    
    public void leave()
    {
    	Customer customer;
    	while(customerqueue.size() > 0)
    	{
    		customer = (Customer)((LinkedList<?>)customerqueue).remove();
    		System.out.println("Customer " + customer.getName() + " who was waiting in the waiting room leaves as the shop is closing.");
    	}
    }
    
    public void newcustomer(Customer customer)
    {
        synchronized (customerqueue)
        {
            if(customerqueue.size() == waitingchairs)
            {
                System.out.println("Customer " + customer.getName() + " leaves as no chair is available in the waiting room.");
                return ;
            }
 
            //adding customers to the queue if spaces are available
            ((LinkedList<Customer>)customerqueue).add(customer);
             
            //when the barbers are sleeping and the first customer enters, this will wake up the barber who called wait on the queue
            if(customerqueue.size()==1)
                customerqueue.notify();
            
            if(freebarber == 0)
            	System.out.println("Customer " + customer.getName() + " is waiting in the waiting room.");
        }
    }
 
    public void cutHair(Barber barber)
    {
    	int barbersleep = 0 ;
        Customer customer;
        
        System.out.println("Barber " + barber.getName() +" is checking for customer in the waiting room.");
        
        synchronized (customerqueue)
        {
        	if (customerqueue.size()==0)
            {
    			System.out.println("Barber " + barber.getName() + " is sleeping as there is no customer in the waiting room.");
    			barbersleep++ ;
            }
            while(customerqueue.size()==0)
            {
                try
                {
                    customerqueue.wait();
                }
                catch(InterruptedException e)
                {
                	//after the executor shutdown, if a barber is still waiting, then this will stop the thread (no deadlock)
                	Thread.currentThread().interrupt();
                	return;
                }
            }
            
            //barber pick up a customer for a haircut and remove the customer from the list
            customer = (Customer)((LinkedList<?>)customerqueue).remove();
            
            if (barbersleep != 0)
            {
            	System.out.println("Customer " + customer.getName() + " wakes up barber "  + barber.getName() + " for a haircut.");
            }
            else
            {
            	System.out.println( "Barber "  + barber.getName() +  " takes customer " + customer.getName() + " for haircut from the waiting room.");
            }
            freebarber--;
        }
        
        //time management for a haircut
        long duration=0;
        try
        {    
            System.out.println( "Barber "  + barber.getName() + " is cutting hair of the customer " + customer.getName());
            duration = (long)((Math.random()*10) + 1);
            TimeUnit.SECONDS.sleep(duration);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
        }
        System.out.println( "Barber "  + barber.getName() +  " completed cutting hair of the customer " + customer.getName() + " in "+ duration + " seconds.");
        
        //time management for seeing off the customer
        duration=0;
        try
        {    
            System.out.println( "Barber "  + barber.getName() + " awakes the customer " + customer.getName() + " and holds the exit door open for him.");
            duration = (long)((Math.random()*5) + 1);
            TimeUnit.SECONDS.sleep(duration);
        }
        catch(InterruptedException e)
        {
            e.printStackTrace();
        }
        System.out.println("Customer " + customer.getName() + " leaves after getting a haircut from " + "Barber "  + barber.getName() +  " in "+ duration + " seconds.");
        
        freebarber++;
    }
}
 
class Barber implements Runnable
{
	String name;
	private static final AtomicInteger idGenerator = new AtomicInteger();
    private final int id;
    private volatile boolean flag = true;
	
	Barbershop salon;
 
    public Barber(Barbershop salon)
    {
        this.salon = salon;
        this.id = idGenerator.incrementAndGet();
    }
    
    //using flag to stop the thread
    public void stopRunning()
    {
        flag = false;
    }
    
    //Setting names of barbers
    public String getName() {
        return name;
    }
    public String getID() {
    	return Integer.toString(id);
    }
    public void setName(String name) {
        this.name = name;
    }
    
    public void run()
    {
        System.out.println("Barber " + this.getName() + " starts working.");
        while(flag)
        {
        	salon.cutHair(this);
        }
        System.out.println("Barber " + this.getName() + " finished work and went home.");
    }
}


class Customer implements Runnable
{
    String name;
    Date inTime;
    private static final AtomicInteger idGenerator = new AtomicInteger();
    private final int id;
 
    Barbershop salon;
 
    public Customer(Barbershop salon)
    {
        this.salon = salon;
        this.id = idGenerator.incrementAndGet();
    }
 
    public String getName() {
        return name;
    }
    
    public String getID() {
    	return Integer.toString(id);
    }
    
    public void setName(String name) {
        this.name = name;
    }
 
    public Date getInTime() {
        return inTime;
    }
 
    public void setInTime(Date inTime) {
        this.inTime = inTime;
    }
 
    public void run()
    {
        haircut();
    }
    private synchronized void haircut()
    {
    	System.out.println("Customer " + this.getName() + " entering the shop at "+ this.getInTime());
    	salon.newcustomer(this); 
    }
}
 
class CustomerCreator implements Runnable
{
    Barbershop salon;
    private volatile boolean flag = true;
    
    public CustomerCreator(Barbershop salon)
    {
        this.salon = salon;
    }
 
    //using flag to stop the thread
    public void stopRunning()
    {
        flag = false;
    }
 
    public void run()
    {
    	
        while(flag)
        {
        	
        	//Creating customer threads at random intervals
            Customer customer = new Customer(salon);
            customer.setInTime(new Date());
            Thread thcustomer = new Thread(customer);
            customer.setName(customer.getID());
            thcustomer.start();
            
            //Random intervals
            try
            {
                TimeUnit.SECONDS.sleep((long)((Math.random()*5)));
            }
            catch(InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
 
}
 