/**
 * A (old) AspectJ stype aspect.
 */
public aspect BeforeAspect
{
    pointcut printPointcut():execution(* ClassToWeave.print(..));

    before() : printPointcut(){
        System.out.println( "before print()" );
    }
}
