pipeline {
  agent any

  triggers {
      cron('*/5 * * * *') //Execute every 5 mins
  }
  environment {
    zoneID = "myzonid" //Find your zone id on Cloudflare, example: 89404d398212d0fccfbc65b82312312132a
    credential_id = "cloudflare".    //You need to configure a credential in Jenkins with credential_id: cloudflare
    targetDomain = "www.example.com" //The targeted A record on Cloudflare
  }
  stages {
    stage('Create environment variables') {
      steps {
        script {
          script {
            env.currentIP = null
          }
        }
      }
    }

    stage('Get current public IP') {
      steps {
        script {
          try {
            env.currentIP = sh(returnStdout: true, script: 'curl ifconfig.me')
          } catch (Exception e) {
            env.currentIP = sh(returnStdout: true, script: 'curl ipinfo.io/ip')
          }
        }
      }
    }

    stage('Update DNS on Cloudflare') {
      steps {
        script {

          withCredentials([usernamePassword(credentialsId: credential_id, passwordVariable: 'KEY', usernameVariable: 'EMAIL')]) {
            def response = sh(returnStdout: true, script: '''
              curl -XGET "https://api.cloudflare.com/client/v4/zones/${zoneID}/dns_records?&direction=asc" -H "Content-Type:application/json" -H "X-Auth-Key:$KEY" -H "X-Auth-Email:$EMAIL"
              '''
            )
            def jsonObj = readJSON text: response
            jsonObj.result.each() {
              if (it.name == env.targetDomain) {
                def cloudflareIP = it.content
                if (cloudflareIP == env.currentIP) {
                  echo "Cloudflare IP is the same as current IP"
                  currentBuild.result = 'SUCCESS'
                  return
                }
                else{
                  echo "Cloudflare IP: " + cloudflareIP
                  echo "Current IP: " + env.currentIP
                  echo "Updating record on Cloudflare"
                  env.recordID = it.id
                  withCredentials([usernamePassword(credentialsId: credential_id, passwordVariable: 'KEY', usernameVariable: 'EMAIL')]) {
                    def updateResponse = sh script: """curl -X PUT "https://api.cloudflare.com/client/v4/zones/${zoneID}/dns_records/${recordID}" -H "X-Auth-Email: $EMAIL" -H "X-Auth-Key: $KEY" -H "Content-Type: application/json" --data '{"type":"A","name":"${it.name}","content":"${env.currentIP}","ttl":3600,"proxied":false}'""", returnStdout: true
                    def updateResult = readJSON text: updateResponse
                    updateResult = updateResult.success
                    if(updateResult){
                      echo "Cloudflare IP updated"
                    }
                    else
                    {
                      echo "Cloudflare IP update failed"
                      currentBuild.result = 'FAILURE'
                      return
                    }
                  
                }

              }

            }

          }

        }
        }
      }
    }
    

    stage('Clean up') {
      steps {
        script {

          cleanWs()
        }
      }
    }

  }
  
}

