import { stageChatFixture } from '../chat/fixture.mjs';
export async function stageIntegrationFixture() {
 const fixture=await stageChatFixture();
 return {...fixture,async close(){
  try{await fixture.query(`(()=>{const ownerUserId={$in:${JSON.stringify(fixture.ids)}};db.integrationTokens.deleteMany({ownerUserId});db.mcpAuthorizations.deleteMany({ownerUserId});return {cleaned:true};})()`);}
  finally{await fixture.close();}
  return {cleaned:true};
 }};
}
