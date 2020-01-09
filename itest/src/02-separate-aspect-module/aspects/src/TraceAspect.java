import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

/**
 * A @AspectJ style aspect.
 */
@Aspect
public class TraceAspect
{
    @Before ("execution (* ClassToWeave.print(..))")
    public void trace()
    { 
        System.out.println("Trace");
    }
}