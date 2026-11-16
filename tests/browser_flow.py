"""Real browser regression. Test accounts are created only on the selected test instance."""
import os,secrets,re,json
from pathlib import Path
from playwright.sync_api import sync_playwright
BASE=os.environ.get('SAFEELO_TEST_URL','http://127.0.0.1:8090')
OUT=Path(os.environ.get('SAFEELO_SCREENSHOTS',str(Path(__file__).parent/'artifacts')));OUT.mkdir(parents=True,exist_ok=True)
password='Teste-'+secrets.token_urlsafe(16)
stamp=secrets.token_hex(5);citizen=f'cidadao-{stamp}@example.org';supporter=f'familiar-{stamp}@example.org';outsider=f'outro-{stamp}@example.org'
def register(page,name,email,role):
 page.get_by_role('button',name='Criar minha conta',exact=True).click() if page.locator('#loginForm').count() else None
 page.locator('#registerName').fill(name);page.locator('#registerEmail').fill(email);page.locator('#registerPassword').fill(password);page.locator(f'input[name="role"][value="{role}"]').check();page.locator('#consent').check();page.locator('#registerForm').get_by_role('button',name='Criar conta →').click();page.locator('#loginForm').wait_for()
def login(page,email):
 page.locator('#loginEmail').fill(email);page.locator('#loginPassword').fill(password);page.locator('#loginForm').get_by_role('button',name='Entrar →').click();page.locator('#nav').get_by_role('button',name='Minha conta',exact=True).wait_for()
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
 c=browser.new_context(timezone_id='America/Sao_Paulo',viewport={'width':1440,'height':1024});s=browser.new_context(timezone_id='America/Sao_Paulo',viewport={'width':1440,'height':1024});x=browser.new_context();cp=c.new_page();sp=s.new_page();xp=x.new_page();errors=[]
 for page in [cp,sp,xp]:page.on('pageerror',lambda err:errors.append(str(err)))
 cp.goto(BASE+'/');cp.get_by_role('heading',name=re.compile('Na dúvida')).wait_for();cp.screenshot(path=str(OUT/'01-inicio-desktop.png'),full_page=True)
 cp.goto(BASE+'/cadastro');cp.locator('#registerForm').wait_for();register(cp,'Maria Teste',citizen,'CITIZEN');login(cp,citizen);cp.locator('#verifyForm').wait_for()
 cp.get_by_role('button',name='Meu Elo',exact=True).click();cp.get_by_role('button',name='＋ Convidar pessoa de confiança').click();cp.locator('#supportEmail').fill(supporter);cp.locator('#relationship').fill('Filha');cp.locator('#inviteForm').get_by_role('button',name='Criar convite').click();cp.locator('#invitationURL').wait_for();invite=cp.locator('#invitationURL').inner_text();cp.get_by_role('button',name='Fechar janela').click() if cp.get_by_role('button',name='Fechar janela').count() else cp.locator('#dialog button[data-action="close"]').click()
 sp.goto(invite);sp.locator('#loginForm').wait_for();register(sp,'Ana Teste',supporter,'SUPPORTER');login(sp,supporter);sp.get_by_role('button',name='Aceitar convite recebido').click();sp.get_by_text('Aguardando sua confirmação',exact=True).wait_for()
 cp.get_by_role('button',name='Meu Elo',exact=True).click();cp.once('dialog',lambda d:d.accept());cp.get_by_role('button',name='Conferi a identidade e confirmo o vínculo').click();cp.get_by_text('Vínculo confirmado',exact=True).wait_for()
 cp.get_by_role('button',name='Verificar',exact=True).click();cp.locator('#suspectContent').fill('Mãe, faça um PIX agora. É urgente. <img src=x onerror=alert(1)>');cp.locator('#verifyForm').get_by_role('button',name='Verificar agora').click();cp.locator('.risk-banner.HIGH').wait_for();assert cp.locator('.submitted img').count()==0
 cp.screenshot(path=str(OUT/'02-resultado-desktop.png'),full_page=True)
 cp.once('dialog',lambda d:d.accept());cp.get_by_role('button',name='♡ Pedir ajuda ao meu Elo').click();cp.get_by_text(re.compile('Pedido de ajuda registrado')).wait_for()
 sp.get_by_role('button',name='Pedidos de ajuda',exact=True).click();sp.locator('.alert-card').wait_for();sp.locator('.alert-card > details > summary').click();sp.get_by_text('Ver o conteúdo analisado',exact=True).click();assert 'Mãe' in sp.locator('.submitted').inner_text();sp.get_by_role('button',name='✓ Marcar como acompanhado').click();sp.get_by_text('✓ Marcado como acompanhado',exact=True).wait_for();sp.screenshot(path=str(OUT/'03-familiar-desktop.png'),full_page=True)
 xp.goto(BASE+'/cadastro');xp.locator('#registerForm').wait_for();register(xp,'Outro Familiar',outsider,'SUPPORTER');login(xp,outsider);xp.get_by_role('heading',name='Nenhum pedido compartilhado.').wait_for();assert x.request.get(BASE+'/api/support/alerts').json()==[]
 cp.get_by_role('button',name='Verificar',exact=True).click();cp.set_viewport_size({'width':390,'height':844});cp.screenshot(path=str(OUT/'04-verificar-mobile.png'),full_page=True);assert cp.evaluate('() => document.documentElement.scrollWidth <= innerWidth+1');cp.get_by_role('button',name='Aumentar ou restaurar o tamanho do texto').click();assert cp.evaluate('() => document.documentElement.scrollWidth <= innerWidth+1');cp.get_by_role('button',name='Aumentar ou restaurar o tamanho do texto').click();cp.set_viewport_size({'width':320,'height':780});assert cp.evaluate('() => document.documentElement.scrollWidth <= innerWidth+1');cp.set_viewport_size({'width':390,'height':844})
 cp.get_by_role('button',name='Meu Elo',exact=True).click();cp.once('dialog',lambda d:d.accept());cp.get_by_role('button',name='Encerrar vínculo').click();cp.get_by_role('heading',name='Seu Elo ainda não está conectado.').wait_for();sp.get_by_role('button',name='Atualizar pedidos',exact=True).click();sp.get_by_role('heading',name='Nenhum pedido compartilhado.').wait_for()
 cp.get_by_role('button',name='Histórico',exact=True).click();cp.get_by_role('button',name='Ver detalhes →').wait_for();cp.get_by_role('button',name='Minha conta',exact=True).click();cp.get_by_role('button',name='Sair da minha conta').click();cp.locator('#loginForm').wait_for();login(cp,citizen);cp.get_by_role('button',name='Histórico',exact=True).click();cp.get_by_role('button',name='Ver detalhes →').wait_for()
 assert not errors,errors
 print(json.dumps({'status':'passed','flows':['registration','real login','invitation','two-sided approval','heuristic analysis','shared alert','acknowledgement','outsider isolation','revocation','persistent history','mobile layout','escaped HTML'],'screenshots':str(OUT)},ensure_ascii=False))
 # Delete fixture accounts after validating to leave a clean application.
 for ctx in [c,s,x]:
  token=ctx.request.get(BASE+'/api/csrf').json();res=ctx.request.delete(BASE+'/api/me',data=json.dumps({'password':password}),headers={'Content-Type':'application/json',token['headerName']:token['token']});assert res.status==200,res.text()
 browser.close()
