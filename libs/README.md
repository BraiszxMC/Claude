# libs/

Aqui va el jar de **BlockBall**, el mismo que tienes en la carpeta `plugins/`
de tu servidor.

```
libs/BlockBall.jar
```

BlockBall no esta publicado en Maven Central, asi que hay que referenciar el
jar directamente. El plugin solo lo usa para compilar (`compileOnly`), no lo
mete dentro del jar final.

Descarga: https://github.com/Shynixn/BlockBall/releases

> Importante: usa la **misma version** de BlockBall que corre en tu servidor.
> Este proyecto se desarrollo contra BlockBall 7.43.0.
