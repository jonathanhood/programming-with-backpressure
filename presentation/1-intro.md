# 1: Dealing with Software Operating Beyond its Limits

Have you ever written software that, at some point in its lifetime, experienced an input
load that was far higher than what you ever expected or tested for it to see? I can
think of a few times that's happened to me:

1. Writing exception packet processing software on an embedded CPU that becomes overwhelmed
   during a packet storm.
2. Writing web backend services that can become unresponsive when the number of users grows
   too large by spawning too many processing threads or otherwise running out of memory.
3. Reading and parsing a file by first loading it completely into memory and then, later,
   crashing when someone feeds a file that is too large.

These all share a common problem. I programmed my applications _assuming_ that my software
(and the hardware executing it) could keep up with _any_ input it received. It turns out
that, in general, this is a faulty assumption. Cursed optimism strikes again!

Luckily, dealing with these situations doesn't have to be _hard_. We just need good tools
to make it easier. In this guide we will talk about applying the concept of _*backpressure*_
to our software with code examples presented in Scala using [Monix](https://monix.io/. This
isn't the languages you work in every day? No problem! It turns out this is one implementation
of [reactive extensions](http://reactivex.io/) which are available in a wide variety of
languages.

## What is Backpressure?

Let's start with the original definition of _backpressure_ which comes from the world of fluid
dynamics:

> Back pressure (or backpressure) is a resistance or force opposing the desired flow of fluid
> through pipes, leading to friction loss and pressure drop.
>   - Credit: [Wikipedia](https://en.wikipedia.org/wiki/Back_pressure)

Ok, let's try again. This time from a more software centric source:

> **When one component is struggling to keep-up**, the system as a whole needs to respond in a sensible
> way. It is unacceptable for the component under stress to fail catastrophically or to drop messages
> in an uncontrolled fashion. **Since it can’t cope and it can’t fail it should communicate the fact that
> it is under stress** to upstream components and so get them to reduce the load. **This back-pressure is
> an important feedback mechanism** that allows systems to gracefully respond to load rather than collapse
> under it. The back-pressure may cascade all the way up to the user, at which point responsiveness may
> degrade, but this mechanism will ensure that the system is resilient under load, and will provide information
> that may allow the system itself to apply other resources to help distribute the load, see Elasticity.
> - Credit: [Reactive Manifesto](https://www.reactivemanifesto.org/glossary#Back-Pressure)
>   (with edits for highlighting)

So, then, backpressure is a feedback mechanism that allows a system to detect and rationally deal with an
overload situation. With such a mechanism I could potentially employ a few obvious strategies for dealing
with these kinds of overloads:

1. I could buffer input until the sys``tem becomes available again to process it.
2. I can drop inputs that can't be immediately processed.
3. I could buffer buffer _and_ drop the oldest inputs when the buffer grows to large.
4. I could signal back to the input source that the overload is occurring.
5. I could spawn more threads / workers / containers/ VMs / etc in order to help deal with the overload.

And, there are more. The list of strategies I can employ grows pretty large when I have this fundamental
tool at my disposal - _backpressure_.

So, how do I buil a system that has backpressure? Let's start to tackle that in the next section by looking
at *Observables* and how they create a system with backpressure weaved into its very infrastructure.
