# How to determine Jakarta EE application deployment dependencies

### why is it called findDependencies?

This is just a temporary name that may already be taken, so we plan to pick a better name when we are further along.

### Why introduce a findDependencies tool?

The idea is to determine Jakarta EE deployment dependencies which might be specialized in various ways for Jakarta EE.  We can learn from the experience of creating the findDependencies prototype exactly what might be useful features.  If we later switch to other existing tools (e.g. OpenJDK `jdeps` tool for example), we will have a good idea of the feature set that we could contribute to whatever tool we switch to later.

#### Why not use existing tools like the OpenJDK `jdeps` tool?

If we can achieve the same goals with existing tools, we can certainly consider creating pull requests for such tools so that we can use them.  For example, we could create a pull request to add war/ear archive file scanning to the OpenJDK `jdeps` which IMO would be a really nice enhancement.   

### What is the expected feature set exactly?

#### Determine which Jakarta EE features are used and how much they are used

It would be really useful to have statistics of how much each Jakarta EE SPEC API class method/field is used.  For a given application deployment, this gives a deep explanation of which technologies are used the most by an application and which Jakarta EE technologies are used very little or not at all.

#### Could the output be used with large language models (LLM)?

That could be possible but needs further exploring.

#### Could the output be useful to application developers?

Yes, it can help developers to better understand what the application deployment archive (e.g. jar/war/ear) file(s) depends on exactly.

The inverse is also true in that the output can help show which [jakarta.ee/specifications](https://jakarta.ee/specifications) are not used by an application.  This information could be useful to feed into future Jakarta EE release development discussions. 

### How to build and run tests

First change into the root folder and build via `mvn clean install` and then change into the findependencies folder and build via `mvn clean install`