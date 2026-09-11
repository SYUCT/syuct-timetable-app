/* Layout only, gated to the known public undergraduate login. Never reads field values. */
(()=>{
 if(!document.querySelector('.login-container .login-form')||!document.querySelector('input[type="password"]'))return;
 if(document.getElementById('syuct-login-layout'))return;
 const style=document.createElement('style');style.id='syuct-login-layout';
 style.textContent='@media(max-width:768px){html,body{margin:0!important;min-height:100%!important}.login-container{min-height:100vh;background:#4e57cd}.login-container .login-blk{min-height:100vh!important;box-sizing:border-box;display:flex;flex-direction:column;justify-content:center;padding:16px!important}.login-container .login-sec{margin:0!important;flex-shrink:0}.login-container .logo{flex-shrink:0}.login-container .login-tit{padding:14px 0!important}}';
 document.head.appendChild(style);
})();
