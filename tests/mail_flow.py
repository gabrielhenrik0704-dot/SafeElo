"""Optional SMTP integration against a local Mailpit instance. No external mail is sent."""
import os,secrets,json,re,urllib.request,time
from playwright.sync_api import sync_playwright
BASE=os.environ.get('SAFEELO_TEST_URL','http://127.0.0.1:8091');MAIL=os.environ.get('SAFEELO_MAILPIT_URL','http://127.0.0.1:8026')
email='reset-'+secrets.token_hex(5)+'@example.org';old='Antes-'+secrets.token_urlsafe(15);new='Depois-'+secrets.token_urlsafe(15)
def inbox():return json.load(urllib.request.urlopen(MAIL+'/api/v1/messages',timeout=5))['messages']
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox']);ctx=browser.new_context();page=ctx.new_page()
 def post(path,data):
  token=ctx.request.get(BASE+'/api/csrf').json();return ctx.request.post(BASE+path,data=json.dumps(data),headers={'Content-Type':'application/json',token['headerName']:token['token']})
 assert post('/api/auth/register',{'name':'Recuperação Teste','email':email,'password':old,'phone':'','role':'CITIZEN','consent':True}).status==201
 page.goto(BASE+'/login');page.locator('#loginEmail').fill(email);page.locator('#loginPassword').fill(old);page.locator('#loginForm .primary').click();page.locator('#verifyForm').wait_for()
 r=post('/api/auth/forgot',{'email':email});assert r.status==200,r.text()
 messages=[m for m in inbox() if any(t['Address']==email for t in m['To'])];assert messages,'No real SMTP message captured.'
 mail=json.load(urllib.request.urlopen(MAIL+'/api/v1/message/'+messages[0]['ID'],timeout=5));url=re.search(r'http://[^\s]+#redefinir=[A-Za-z0-9_-]+',mail['Text']).group();token=url.split('#redefinir=')[1]
 page.goto(url);page.locator('#resetForm').wait_for();page.locator('#resetPassword').fill(new);page.locator('#resetForm .primary').click();page.locator('#loginForm').wait_for();page.locator('#loginEmail').fill(email);page.locator('#loginPassword').fill(new);page.locator('#loginForm .primary').click();page.locator('#verifyForm').wait_for()
 assert post('/api/auth/reset',{'token':token,'password':new}).status==400
 for m in inbox():
  if 'pediu sua ajuda' in m['Subject']:
   content=json.load(urllib.request.urlopen(MAIL+'/api/v1/message/'+m['ID'],timeout=5))['Text'];assert 'PRIVADO' not in content and '<img' not in content and 'Mãe' not in content
 csrf=ctx.request.get(BASE+'/api/csrf').json();r=ctx.request.delete(BASE+'/api/me',data=json.dumps({'password':new}),headers={'Content-Type':'application/json',csrf['headerName']:csrf['token']});assert r.status==200,r.text()
 print('Passed: actual SMTP acceptance, private reset link, browser password reset while signed in, new login, token replay rejection and minimal-content notification.');browser.close()
